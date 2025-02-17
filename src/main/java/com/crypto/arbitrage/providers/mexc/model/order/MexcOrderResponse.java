package com.crypto.arbitrage.providers.mexc.model.order;

import com.crypto.arbitrage.providers.mexc.model.MexcData;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;


@Data
public class MexcOrderResponse implements MexcData {

    @JsonProperty("c")
    private String channel;

    @JsonProperty("d")
    private MexcOrderInfo orderInfo;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("t")
    private long eventTime;

    @Data
    public static class MexcOrderInfo {

        @JsonProperty("A")
        @JsonDeserialize(using = BigDecimalDeserializer.class)
        private BigDecimal remainAmount;

        @JsonProperty("O")
        private long createTime;

        @JsonProperty("S")
        private int tradeType; // 1: buy, 2: sell

        @JsonProperty("V")
        @JsonDeserialize(using = BigDecimalDeserializer.class)
        private BigDecimal remainQuantity;

        @JsonProperty("a")
        @JsonDeserialize(using = BigDecimalDeserializer.class)
        private BigDecimal amount;

        @JsonProperty("c")
        private String clientOrderId;

        @JsonProperty("i")
        private String orderId;

        @JsonProperty("m")
        private int isMaker;

        @JsonProperty("o")
        private int orderType; // LIMIT_ORDER(1), POST_ONLY(2), etc.

        @JsonProperty("p")
        @JsonDeserialize(using = BigDecimalDeserializer.class)
        private BigDecimal price;

        @JsonProperty("s")
        private int status;

        @JsonProperty("v")
        @JsonDeserialize(using = BigDecimalDeserializer.class)
        private BigDecimal quantity;

        @JsonProperty("ap")
        @JsonDeserialize(using = BigDecimalDeserializer.class)
        private BigDecimal avgPrice;

        @JsonProperty("cv")
        @JsonDeserialize(using = BigDecimalDeserializer.class)
        private BigDecimal cumulativeQuantity;

        @JsonProperty("ca")
        @JsonDeserialize(using = BigDecimalDeserializer.class)
        private BigDecimal cumulativeAmount;
    }
}