package com.crypto.arbitrage.service.datasource;

import com.crypto.arbitrage.data.ExecutionRequest;
import com.crypto.arbitrage.data.Topic;
import com.crypto.arbitrage.data.TopicMessage;
import com.crypto.arbitrage.data.entity.DatasourceInfo;
import com.crypto.arbitrage.service.CEXAccountService;
import com.crypto.arbitrage.service.messaging.PublishSubscribeService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import velox.api.layer1.Layer1ApiDataListener;
import velox.api.layer1.Layer1ApiInstrumentListener;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.MarketMode;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.layers.utils.OrderBook;

import java.util.Map;

@Component
@Scope("prototype")
@Slf4j
@RequiredArgsConstructor
public class CEXEngine  implements Layer1ApiDataListener, Layer1ApiInstrumentListener {
    @Getter
    private volatile int bidPrice;
    @Getter
    private volatile int askPrice;
    private volatile int lastTradeAskSize;
    private volatile int lastTradeBidSize;
    private final PublishSubscribeService publishSubscribeService;
    private final CEXAccountService cexAccountService;
    private DatasourceInfo datasourceInfo;
    private String alias;
    private OrderBook orderBook = new OrderBook();
    private Map<String, InstrumentInfo> aliasToInstrumentInfo;


    public void initialize(DatasourceInfo dataSourceInfo,
                           Map<String, InstrumentInfo> aliasToInstrumentInfo) {
        this.datasourceInfo = dataSourceInfo;
        this.aliasToInstrumentInfo = aliasToInstrumentInfo;
        this.alias = datasourceInfo.getSymbol() ;
        clearOrderBook();
    }

    public void disable() {
    }

    @Override
    public void onTrade(String alias, double price, int size, TradeInfo tradeInfo) {
        InstrumentInfo instrumentInfo = aliasToInstrumentInfo.get(alias);
        if (instrumentInfo == null || !instrumentInfo.symbol.equals(this.alias)  || size == 0) {
            return;
        }

        int tradePrice = (int) Math.round(price);

        if (tradeInfo.isBidAggressor) {
            lastTradeBidSize = size;
        } else {
            lastTradeAskSize = size;
        }
        execute(tradePrice, !tradeInfo.isBidAggressor, false);
    }


    @Override
    public void onDepth(String alias, boolean isBid, int price, int size) {
        InstrumentInfo instrumentInfo = aliasToInstrumentInfo.get(alias);
        if (instrumentInfo == null || !instrumentInfo.symbol.equals(this.alias))
            return;

        orderBook.onUpdate(isBid, price, size);
        updateOrderBookBbo();
    }

    private void updateOrderBookBbo() {
        if (orderBook.getBidMap().isEmpty() || orderBook.getAskMap().isEmpty())
            return;

        int bidPrice = orderBook.getBestBidPriceOrNone();
        int askPrice = orderBook.getBestAskPriceOrNone();

        if (bidPrice == Integer.MAX_VALUE || askPrice == Integer.MAX_VALUE ||
                bidPrice > askPrice || (askPrice - bidPrice) > 20) {
            return;
        }


        if (this.bidPrice != bidPrice || this.askPrice != askPrice) {
            processExecutions();
        }
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;

    }

    @Override
    public void onMarketMode(String s, MarketMode marketMode) {

    }

    private String normalizeAlias(String alias) {
        return alias.replace(":SPOT", "");
    }


    public void processExecutions() {
        execute(bidPrice, true, true);
        execute(askPrice, false, true);
    }

    private void execute(int priceToProcess, boolean isBuyTrade, boolean isTriggeredByBbo) {
        int lastTradeSize = isBuyTrade ? lastTradeBidSize : lastTradeAskSize;

        // TODO: we need check somehow is there need to publish this request
        boolean hasExecutableOrders = false;
        if (hasExecutableOrders) {
            publishSubscribeService.publish(Topic.EXECUTION_REQUEST, new TopicMessage(null,
                    new ExecutionRequest(
                            datasourceInfo.getAlias(),
                            priceToProcess * datasourceInfo.getPipsSize(),
                            lastTradeSize, isBuyTrade, isTriggeredByBbo)
            ));
        }
    }
    private void clearOrderBook() {
        if (orderBook != null) {
            orderBook.clear();
        }
    }

    @Override
    public void onInstrumentAdded(String s, InstrumentInfo instrumentInfo) {
        aliasToInstrumentInfo.put(s, instrumentInfo);
    }


    @Override
    public void onInstrumentRemoved(String s) {
        aliasToInstrumentInfo.remove(s);
    }

    @Override
    public void onInstrumentNotFound(String s, String s1, String s2) {

    }

    @Override
    public void onInstrumentAlreadySubscribed(String s, String s1, String s2) {

    }

    public boolean isDataValid() {
        int bestBid = orderBook.getBestBidPriceOrNone();
        int bestAsk = orderBook.getBestAskPriceOrNone();

        return bestBid < bestAsk;
    }


    public double getPips() {
        return datasourceInfo.getPipsSize();
    }

    public long getBidSize() {
        return orderBook.getBidMap().values().stream().mapToLong(Long::longValue).sum();
    }

    public long getAskSize() {
        return orderBook.getAskMap().values().stream().mapToLong(Long::longValue).sum();
    }
}
