package com.crypto.arbitrage.providers.mexc.service.parser;

import com.crypto.arbitrage.providers.mexc.model.MexcData;
import com.crypto.arbitrage.providers.mexc.model.account.BalanceChangeType;
import com.crypto.arbitrage.providers.mexc.model.account.MexcAccountBalance;
import com.crypto.arbitrage.providers.mexc.model.account.MexcBalanceEvent;
import com.crypto.arbitrage.providers.mexc.model.common.MexcSubscriptionResp;
import com.crypto.arbitrage.providers.mexc.model.depth.MexcDepthData;
import com.crypto.arbitrage.providers.mexc.model.depth.MexcDepthEntry;
import com.crypto.arbitrage.providers.mexc.model.event.MexcDepthEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcSubscriptionEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcTradeEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcWebSocketSessionStatusEvent;
import com.crypto.arbitrage.providers.mexc.model.instrument.MexcInstrumentEvent;
import com.crypto.arbitrage.providers.mexc.model.instrument.MexcSubscribedInstrumentEvent;
import com.crypto.arbitrage.providers.mexc.model.instrument.MexcUnsubscribedInstrumentEvent;
import com.crypto.arbitrage.providers.mexc.model.order.MexcExecutionEvent;
import com.crypto.arbitrage.providers.mexc.model.order.MexcExecutionInfo;
import com.crypto.arbitrage.providers.mexc.model.trade.MexcTradeStream;
import com.crypto.arbitrage.service.messaging.PublishSubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import velox.api.layer1.data.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MexcDataProcessor {

    private final ApplicationEventPublisher publisher;
    private Map<String, InstrumentInfo> subscribedInstruments = new HashMap<>();


    public void process(MexcData mexcData) {
        if (mexcData instanceof MexcDepthData mexcDepthData) {
            processDepth(mexcDepthData);
        } else if (mexcData instanceof MexcTradeStream mexcTradeStream) {
            processTrade(mexcTradeStream);
        } else if (mexcData instanceof MexcSubscriptionResp mexcSubscriptionResp) {
            processSubscriptionMessage(mexcSubscriptionResp);
        } else if (mexcData instanceof MexcAccountBalance mexcAccountBalance) {
            processBalance(mexcAccountBalance);
        } else if (mexcData instanceof MexcExecutionInfo mexcExecutionInfo) {
            processExecutionInfo(mexcExecutionInfo);
        }

        else {
            log.warn("Unknown MexcData type: {}", mexcData);
        }
    }

    private void processExecutionInfo(MexcExecutionInfo mexcExecutionInfo) {
        InstrumentInfo instrumentInfo = subscribedInstruments.get(mexcExecutionInfo.getSymbol());
        int size = (int) (mexcExecutionInfo.getDealsInfo().getQuantity() * instrumentInfo.sizeMultiplier);
        double price = mexcExecutionInfo.getDealsInfo().getPrice();
        ExecutionInfo executionInfo = new ExecutionInfo(mexcExecutionInfo.getDealsInfo().getOrderId(), size, price, mexcExecutionInfo.getDealsInfo().getTradeId(), mexcExecutionInfo.getEventTime());
        log.info("Processing and publishing info: {}", executionInfo);
        publisher.publishEvent(new MexcExecutionEvent(executionInfo));
    }

    private void processBalance(MexcAccountBalance mexcAccountBalance) {
        BalanceChangeType changedType = mexcAccountBalance.getAccountUpdates().getChangedType();

        // only update balance if it's a deposit, withdraw or balance changes after an order
        if (BalanceChangeType.ENTRUST.equals(changedType) || BalanceChangeType.WITHDRAW.equals(changedType) || BalanceChangeType.DEPOSIT.equals(changedType)) {
            BalanceInfoBuilder balanceInfoBuilder = new BalanceInfoBuilder();
            BalanceInfo.BalanceInCurrency balanceInCurrency = new BalanceInfo.BalanceInCurrency(
                    mexcAccountBalance.getAccountUpdates().getFreeBalance(), 0, 0, 0, 0, mexcAccountBalance.getAccountUpdates().getAsset(), 1.0);
            ArrayList<BalanceInfo.BalanceInCurrency> balanceInCurrencies = new ArrayList<>();
            balanceInCurrencies.add(balanceInCurrency);
            balanceInfoBuilder.setBalancesInCurrency(balanceInCurrencies);

            BalanceInfo balanceInfo = balanceInfoBuilder.build();
            publisher.publishEvent(new MexcBalanceEvent(balanceInfo));
        }
    }

    private void processDepth(MexcDepthData mexcDepthData) {
        String symbol = mexcDepthData.getSymbol();
        InstrumentInfo instrumentInfo = subscribedInstruments.get(symbol);

        processDepthLevels(mexcDepthData.getAsks(), symbol, instrumentInfo, false);
        processDepthLevels(mexcDepthData.getBids(), symbol, instrumentInfo, true);
    }

    private void processDepthLevels(List<MexcDepthEntry> levels, String symbol, InstrumentInfo instrumentInfo, boolean isBid) {
        if (levels == null) return;

        levels.stream()
                .map(level -> new MexcDepthEvent(
                        symbol,
                        isBid,
                        (int) (level.getPrice() / instrumentInfo.pips),
                        (int) (level.getQuantity() * instrumentInfo.sizeMultiplier)
                ))
                .forEach(publisher::publishEvent);
    }


    public void processTrade(@NonNull MexcTradeStream mexcTradeStream) {
        String symbol = mexcTradeStream.getSymbol();
        InstrumentInfo instrumentInfo = subscribedInstruments.get(symbol);
        if (mexcTradeStream.getMexcTradeData() != null && mexcTradeStream.getMexcTradeData().getDeals() != null) {
            mexcTradeStream.getMexcTradeData().getDeals().stream()
                    .map(deal -> {
                        double price = deal.getPrice();
                        int size = (int) (deal.getQuantity() * instrumentInfo.sizeMultiplier);
                        // tradeType 1: buy (bid aggressive), 2: sell (bid not aggressive)
                        boolean isBidAggressor = (deal.getTradeType() == 1);
                        TradeInfo tradeInfo = new TradeInfo(false, isBidAggressor);
                        return new MexcTradeEvent(symbol, price, size, tradeInfo);
                    })
                    .forEach(publisher::publishEvent);
        }
    }

    private void processSubscriptionMessage(@NonNull MexcSubscriptionResp mexcSubscriptionResp) {
        MexcSubscriptionEvent event = new MexcSubscriptionEvent();
        event.setChannel(mexcSubscriptionResp.getMsg());
        publisher.publishEvent(event);
    }

    @EventListener
    public void onInstrumentEvent(@NonNull MexcInstrumentEvent event) {
        if (event instanceof MexcSubscribedInstrumentEvent subscribedInstrumentEvent) {
            subscribedInstruments.put(subscribedInstrumentEvent.getInstrumentInfo().symbol, subscribedInstrumentEvent.getInstrumentInfo());
        } else if (event instanceof MexcUnsubscribedInstrumentEvent unsubscribedInstrumentEvent) {
            subscribedInstruments.remove(unsubscribedInstrumentEvent.getSymbol());
        } else {
            log.warn("Unknown MexcInstrumentEvent type: {}", event);
        }
    }


    private int toIntOrMax(long value, int max) {
        return (int) Math.min(value, max);
    }
}