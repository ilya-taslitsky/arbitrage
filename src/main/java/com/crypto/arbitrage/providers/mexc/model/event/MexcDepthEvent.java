package com.crypto.arbitrage.providers.mexc.model.event;

import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MexcDepthEvent implements MexcExchangeEvent{
    private String symbol;
    private boolean isBid;
    private int price;
    private int size;
}