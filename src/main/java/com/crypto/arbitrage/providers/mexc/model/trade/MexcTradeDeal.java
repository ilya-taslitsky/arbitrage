package com.crypto.arbitrage.providers.mexc.model.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MexcTradeDeal {
    @JsonProperty("S")
    private int tradeType;   // 1 = buy, 2 = sell

    @JsonProperty("p")
    private double price;

    @JsonProperty("t")
    private long dealTime;

    @JsonProperty("v")
    private double quantity;
}
