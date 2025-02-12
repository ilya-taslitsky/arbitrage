package com.crypto.arbitrage.providers.mexc.service.parser;

import com.crypto.arbitrage.providers.mexc.model.MexcData;
import com.crypto.arbitrage.providers.mexc.model.common.MexcSubscriptionResp;
import com.crypto.arbitrage.providers.mexc.model.depth.MexcDepthData;
import com.crypto.arbitrage.providers.mexc.model.event.MexcDepthEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcSubscriptionEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcTradeEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcWebSocketSessionStatusEvent;
import com.crypto.arbitrage.providers.mexc.model.trade.MexcTradeStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import velox.api.layer1.data.TradeInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class MexcDataProcessor {

    private final ApplicationEventPublisher publisher;

    public void process(MexcData mexcData) {
        if (mexcData instanceof MexcDepthData mexcDepthData) {
            processDepth(mexcDepthData);
        } else if (mexcData instanceof MexcTradeStream mexcTradeStream) {
            processTrade(mexcTradeStream);
        } else if (mexcData instanceof MexcSubscriptionResp mexcSubscriptionResp) {
            processSubscriptionMessage(mexcSubscriptionResp);
        } else {
            log.info("Unknown MexcData type: {}", mexcData);
        }
    }

    private void processDepth(MexcDepthData mexcDepthData) {
        String symbol = mexcDepthData.getSymbol();
        // Process ask levels (isBid = false)
        if (mexcDepthData.getAsks() != null) {
            mexcDepthData.getAsks().stream()
                    .map(ask -> {
                        int price = new BigDecimal(ask.getPrice())
                                .setScale(0, RoundingMode.HALF_UP)
                                .intValue();
                        int size = new BigDecimal(ask.getQuantity())
                                .setScale(0, RoundingMode.HALF_UP)
                                .intValue();
                        return new MexcDepthEvent(symbol, false, price, size);
                    })
                    .forEach(publisher::publishEvent);
        }
        // Process bid levels (isBid = true)
        if (mexcDepthData.getBids() != null) {
            mexcDepthData.getBids().stream()
                    .map(bid -> {
                        int price = new BigDecimal(bid.getPrice())
                                .setScale(0, RoundingMode.HALF_UP)
                                .intValue();
                        int size = new BigDecimal(bid.getQuantity())
                                .setScale(0, RoundingMode.HALF_UP)
                                .intValue();
                        return new MexcDepthEvent(symbol, true, price, size);
                    })
                    .forEach(publisher::publishEvent);
        }
    }

    public void processTrade(@NonNull MexcTradeStream mexcTradeStream) {
        String symbol = mexcTradeStream.getSymbol();
        if (mexcTradeStream.getMexcTradeData() != null && mexcTradeStream.getMexcTradeData().getDeals() != null) {
            mexcTradeStream.getMexcTradeData().getDeals().stream()
                    .map(deal -> {
                        double price = Double.parseDouble(deal.getPrice());
                        int size = new BigDecimal(deal.getQuantity())
                                .setScale(0, RoundingMode.HALF_UP)
                                .intValue();
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
        publisher.publishEvent(new MexcWebSocketSessionStatusEvent(true));
    }
}