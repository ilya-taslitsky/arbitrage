package com.crypto.arbitrage.providers.mexc.service.parser;

import velox.api.layer1.data.TradeInfo;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import com.crypto.arbitrage.providers.mexc.model.trade.TradeStream;
import com.crypto.arbitrage.providers.mexc.model.event.MexcTradeEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class MexcTradeMessageProcessor {

    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public MexcTradeMessageProcessor(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void process(TradeStream tradeStream) {
        String symbol = tradeStream.getSymbol();
        if (tradeStream.getTradeData() != null && tradeStream.getTradeData().getDeals() != null) {
            tradeStream.getTradeData().getDeals().stream()
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
                    .forEach(eventPublisher::publishEvent);
        }
    }
}

