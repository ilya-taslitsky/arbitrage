package com.crypto.arbitrage.providers.mexc.model.depth;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MexcDepthEntry {
    @JsonProperty("p")
    private double price;

    @JsonProperty("v")
    private double quantity;
}