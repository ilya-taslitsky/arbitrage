package com.crypto.arbitrage.providers.mexc.service.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;
import com.crypto.arbitrage.providers.mexc.model.depth.DepthData;
import com.crypto.arbitrage.providers.mexc.model.event.MexcDepthEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class MexcDepthMessageProcessor {

    private final ApplicationEventPublisher eventPublisher;

    public void process(String symbol, DepthData depthData) {
        // Process ask levels (isBid = false)
        if (depthData.getAsks() != null) {
            depthData.getAsks().stream()
                    .map(ask -> {
                        int price = new BigDecimal(ask.getPrice())
                                .setScale(0, RoundingMode.HALF_UP)
                                .intValue();
                        int size = new BigDecimal(ask.getQuantity())
                                .setScale(0, RoundingMode.HALF_UP)
                                .intValue();
                        return new MexcDepthEvent(symbol, false, price, size);
                    })
                    .forEach(eventPublisher::publishEvent);
        }
        // Process bid levels (isBid = true)
        if (depthData.getBids() != null) {
            depthData.getBids().stream()
                    .map(bid -> {
                        int price = new BigDecimal(bid.getPrice())
                                .setScale(0, RoundingMode.HALF_UP)
                                .intValue();
                        int size = new BigDecimal(bid.getQuantity())
                                .setScale(0, RoundingMode.HALF_UP)
                                .intValue();
                        return new MexcDepthEvent(symbol, true, price, size);
                    })
                    .forEach(eventPublisher::publishEvent);
        }
    }
}
