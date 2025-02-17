package com.crypto.arbitrage.providers.mexc.model.depth;

import com.crypto.arbitrage.providers.mexc.model.MexcData;
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
public class MexcDepthData implements MexcData {
    @JsonProperty("asks")
    private List<MexcDepthEntry> asks;

    @JsonProperty("bids")
    private List<MexcDepthEntry> bids;

    @JsonProperty("e")
    private String event;
}

