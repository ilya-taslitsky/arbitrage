package com.crypto.arbitrage.providers.mexc.model.depth;

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
public class DepthData {
    @JsonProperty("asks")
    private List<DepthEntry> asks;

    @JsonProperty("bids")
    private List<DepthEntry> bids;

    @JsonProperty("e")
    private String event;

    @JsonProperty("r")
    private long version;

    @JsonProperty("t")
    private long timestamp;
}

