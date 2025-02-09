package com.crypto.arbitrage.providers.mexc.model.trade;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TradeData {
    @JsonProperty("deals")
    private List<TradeDeal> deals;

    @JsonProperty("e")
    private String eventType;
}
