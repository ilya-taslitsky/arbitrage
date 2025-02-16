package com.crypto.arbitrage.providers.mexc.model.order;

import com.crypto.arbitrage.providers.mexc.model.MexcData;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@Data
public class MexcExecutionInfo implements MexcData {

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("t")
    private long eventTime;

    @JsonProperty("d")
    private DealsInfo dealsInfo;

    @Data
    public static class DealsInfo {

        @JsonProperty("S")
        private int tradeType; // 1: buy, 2: sell

        @JsonProperty("T")
        private long tradeTime;

        @JsonProperty("c")
        private String clientOrderId;

        @JsonProperty("i")
        private String orderId;

        @JsonProperty("m")
        private int isMaker; // Typically a boolean flag represented as int (0 or 1)

        @JsonProperty("p")
        private double price;

        @JsonProperty("st")
        private byte isSelfTrade; // Byte for small integer flag

        @JsonProperty("t")
        private String tradeId;

        @JsonProperty("v")
        private double quantity;

        @JsonProperty("a")
        private double dealsAmount;

        @JsonProperty("n")
        private double commissionFee;

        @JsonProperty("N")
        private String commissionAsset;
    }
}
