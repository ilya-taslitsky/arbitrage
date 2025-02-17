package com.crypto.arbitrage.providers.mexc.model.depth;

import com.crypto.arbitrage.providers.mexc.model.MexcData;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BookDepthResponse implements MexcData {

    @JsonProperty("c")
    private String channel;

    @JsonProperty("d")
    private MexcDepthData depthData;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("t")
    private long eventTime;
}