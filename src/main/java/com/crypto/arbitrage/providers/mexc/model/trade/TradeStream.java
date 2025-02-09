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
public class TradeStream {
    @JsonProperty("c")
    private String channel;

    @JsonProperty("d")
    private TradeData tradeData;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("t")
    private long eventTime;
}
