package com.crypto.arbitrage.providers.mexc.service.messages;

import com.crypto.arbitrage.providers.mexc.MexcProvider;
import com.crypto.arbitrage.providers.mexc.model.MexcData;
import com.crypto.arbitrage.providers.mexc.model.account.BalanceChangeType;
import com.crypto.arbitrage.providers.mexc.model.account.MexcAccountBalance;
import com.crypto.arbitrage.providers.mexc.model.account.MexcBalanceEvent;
import com.crypto.arbitrage.providers.mexc.model.common.MexcSubscriptionResp;
import com.crypto.arbitrage.providers.mexc.model.depth.BookDepthResponse;
import com.crypto.arbitrage.providers.mexc.model.depth.MexcDepthData;
import com.crypto.arbitrage.providers.mexc.model.depth.MexcDepthEntry;
import com.crypto.arbitrage.providers.mexc.model.event.MexcDepthEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcSubscriptionEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcTradeEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcWebSocketSessionStatusEvent;
import com.crypto.arbitrage.providers.mexc.model.instrument.MexcInstrumentEvent;
import com.crypto.arbitrage.providers.mexc.model.instrument.MexcSubscribedInstrumentEvent;
import com.crypto.arbitrage.providers.mexc.model.instrument.MexcUnsubscribedInstrumentEvent;
import com.crypto.arbitrage.providers.mexc.model.order.*;
import com.crypto.arbitrage.providers.mexc.model.trade.MexcTradeStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import velox.api.layer1.data.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MexcDataProcessor {

  private final ApplicationEventPublisher publisher;
  private final Map<String, InstrumentInfo> knowInstruments = new HashMap<>();

  public void process(MexcData mexcData) {
    if (mexcData instanceof BookDepthResponse bookDepthResponse) {
      processDepth(bookDepthResponse);
    } else if (mexcData instanceof MexcTradeStream mexcTradeStream) {
      processTrade(mexcTradeStream);
    } else if (mexcData instanceof MexcSubscriptionResp mexcSubscriptionResp) {
      processSubscriptionMessage(mexcSubscriptionResp);
    } else if (mexcData instanceof MexcAccountBalance mexcAccountBalance) {
      processBalance(mexcAccountBalance);
    } else if (mexcData instanceof MexcExecutionInfo mexcExecutionInfo) {
      processExecutionInfo(mexcExecutionInfo);
    } else if (mexcData instanceof MexcOrderResponse mexcOrderInfo) {
      log.info(mexcOrderInfo.getOrderInfo().toString());
      processOrderInfo(mexcOrderInfo);
    } else {
      log.warn("Unknown MexcData type: {}", mexcData);
    }
  }

  private void processOrderInfo(MexcOrderResponse mexcOrderInfo) {
    OrderInfo orderInfo = getOrderInfo(mexcOrderInfo);
    log.info("Processing and publishing info: {}", orderInfo);
    publisher.publishEvent(new MexcOrderInfoEvent(new OrderInfoUpdate(orderInfo)));
  }

  private void processExecutionInfo(MexcExecutionInfo mexcExecutionInfo) {
    InstrumentInfo instrumentInfo = knowInstruments.get(mexcExecutionInfo.getSymbol());
    int size =
        (int) (mexcExecutionInfo.getDealsInfo().getQuantity() * instrumentInfo.sizeMultiplier);
    double price = mexcExecutionInfo.getDealsInfo().getPrice();
    ExecutionInfo executionInfo =
        new ExecutionInfo(
            mexcExecutionInfo.getDealsInfo().getOrderId(),
            size,
            price,
            mexcExecutionInfo.getDealsInfo().getTradeId(),
            mexcExecutionInfo.getEventTime());
    log.info("Processing and publishing info: {}", executionInfo);
    publisher.publishEvent(new MexcExecutionEvent(executionInfo));
  }

  private void processBalance(MexcAccountBalance mexcAccountBalance) {
    BalanceChangeType changedType = mexcAccountBalance.getAccountUpdates().getChangedType();
    log.info("Processing balance update: {}", mexcAccountBalance);
    // only update balance if it's a deposit, withdraw or balance changes after an order
    if (BalanceChangeType.ENTRUST.equals(changedType)
        || BalanceChangeType.WITHDRAW.equals(changedType)
        || BalanceChangeType.DEPOSIT.equals(changedType)) {
      BalanceInfoBuilder balanceInfoBuilder = new BalanceInfoBuilder();
      BalanceInfo.BalanceInCurrency balanceInCurrency =
          new BalanceInfo.BalanceInCurrency(
              mexcAccountBalance.getAccountUpdates().getFreeBalance(),
              0,
              0,
              0,
              0,
              mexcAccountBalance.getAccountUpdates().getAsset(),
              1.0);
      ArrayList<BalanceInfo.BalanceInCurrency> balanceInCurrencies = new ArrayList<>();
      balanceInCurrencies.add(balanceInCurrency);
      balanceInfoBuilder.setBalancesInCurrency(balanceInCurrencies);

      BalanceInfo balanceInfo = balanceInfoBuilder.build();
      publisher.publishEvent(new MexcBalanceEvent(balanceInfo));
    }
  }

  private void processDepth(BookDepthResponse bookDepthResponse) {
    String symbol = bookDepthResponse.getSymbol();
    MexcDepthData depthData = bookDepthResponse.getDepthData();
    InstrumentInfo instrumentInfo = knowInstruments.get(symbol);

    processDepthLevels(depthData.getAsks(), symbol, instrumentInfo, false);
    processDepthLevels(depthData.getBids(), symbol, instrumentInfo, true);
  }

  private void processDepthLevels(
      List<MexcDepthEntry> levels, String symbol, InstrumentInfo instrumentInfo, boolean isBid) {
    if (levels == null) return;

    levels.stream()
        .map(
            level ->
                new MexcDepthEvent(
                    symbol,
                    isBid,
                    (int) (level.getPrice() / instrumentInfo.pips),
                    (int) (level.getQuantity() * instrumentInfo.sizeMultiplier)))
        .forEach(publisher::publishEvent);
  }

  public void processTrade(@NonNull MexcTradeStream mexcTradeStream) {
    String symbol = mexcTradeStream.getSymbol();
    InstrumentInfo instrumentInfo = knowInstruments.get(symbol);
    if (mexcTradeStream.getMexcTradeData() != null
        && mexcTradeStream.getMexcTradeData().getDeals() != null) {
      mexcTradeStream.getMexcTradeData().getDeals().stream()
          .map(
              deal -> {
                double price = deal.getPrice();
                int size = (int) (deal.getQuantity() * instrumentInfo.sizeMultiplier);
                // tradeType 1: buy (bid aggressive), 2: sell (bid not aggressive)
                boolean isBidAggressor = isBid(deal.getTradeType());
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
    publisher.publishEvent(new MexcWebSocketSessionStatusEvent(true));
  }

  @EventListener
  public void onInstrumentEvent(@NonNull MexcInstrumentEvent event) {
    if (event instanceof MexcSubscribedInstrumentEvent subscribedInstrumentEvent) {
      knowInstruments.put(
          subscribedInstrumentEvent.getInstrumentInfo().symbol,
          subscribedInstrumentEvent.getInstrumentInfo());
    } else if (event instanceof MexcUnsubscribedInstrumentEvent unsubscribedInstrumentEvent) {
      // knowInstruments.remove(unsubscribedInstrumentEvent.getSymbol());
    } else {
      log.warn("Unknown MexcInstrumentEvent type: {}", event);
    }
  }

  // cant handle stp orders
  private OrderInfo getOrderInfo(MexcOrderResponse mexcOrderInfoResponse) {
    String symbol = mexcOrderInfoResponse.getSymbol();
    long time = mexcOrderInfoResponse.getEventTime();
    MexcOrderResponse.MexcOrderInfo mexcOrderInfo = mexcOrderInfoResponse.getOrderInfo();

    InstrumentInfo instrumentInfo = knowInstruments.get(symbol);

    int filled =
        (int) (mexcOrderInfo.getCumulativeQuantity().doubleValue() * instrumentInfo.sizeMultiplier);
    int remaining =
        (int) (mexcOrderInfo.getRemainQuantity().doubleValue() * instrumentInfo.sizeMultiplier);
    boolean isBid = isBid(mexcOrderInfo.getTradeType());
    OrderType orderType = getOrderType(mexcOrderInfo.getOrderType());

    OrderInfoBuilder orderInfoBuilder =
        new OrderInfoBuilder(
            symbol,
            mexcOrderInfo.getOrderId(),
            isBid,
            orderType,
            mexcOrderInfo.getClientOrderId(),
            false);
    orderInfoBuilder.setFilled(filled);
    orderInfoBuilder.setUnfilled(remaining);
    orderInfoBuilder.setAverageFillPrice(mexcOrderInfo.getAvgPrice().doubleValue());
    orderInfoBuilder.setDuration(OrderDuration.IOC);
    orderInfoBuilder.setStatus(fromMexcStatus(mexcOrderInfo.getStatus()));
    orderInfoBuilder.setLimitPrice(mexcOrderInfo.getPrice().doubleValue());
    orderInfoBuilder.setModificationUtcTime(time);
    orderInfoBuilder.setExchangeId(MexcProvider.NAME);

    return orderInfoBuilder.build();
  }

  public OrderStatus fromMexcStatus(int status) {
    return switch (status) {
      case 1, 3 -> OrderStatus.WORKING; // (1) Новый ордер (3) Частично исполнен, но ещё активен
      case 2, 5 -> OrderStatus.FILLED; // (2) Полностью исполнен (5) Частично исполнен + отменён
      case 4 -> OrderStatus.CANCELLED; // Полностью отменён
      default -> throw new IllegalArgumentException("Unknown status: " + status);
    };
  }

  private OrderType getOrderType(int mexOrderType) {
    return switch (mexOrderType) {
      case 1:
        yield OrderType.LMT;
      case 5:
        yield OrderType.MKT;
      case 100:
        yield OrderType.STP_LMT;
      default:
        throw new IllegalStateException("Unexpected value: " + mexOrderType);
    };
  }

  private boolean isBid(int tradeType) {
    return tradeType == 1;
  }
}
