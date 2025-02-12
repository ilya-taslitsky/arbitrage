package com.crypto.arbitrage.providers.mexc.model.event;

import lombok.*;
import velox.api.layer1.data.TradeInfo;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MexcTradeEvent implements MexcExchangeEvent {
    private String symbol;
    private double price;
    private int size;
    private TradeInfo tradeInfo;
}

