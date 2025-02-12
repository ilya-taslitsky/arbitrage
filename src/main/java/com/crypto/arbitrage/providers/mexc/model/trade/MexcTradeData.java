package com.crypto.arbitrage.providers.mexc.model.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MexcTradeData {
    @JsonProperty("deals")
    private List<MexcTradeDeal> deals;

    @JsonProperty("e")
    private String eventType;
}
