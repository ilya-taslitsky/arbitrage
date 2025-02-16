package com.crypto.arbitrage.providers.mexc.model.order;

import com.crypto.arbitrage.providers.mexc.model.MexcData;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MexcOrderInfo implements MexcData {

    @JsonProperty("A")
    private BigDecimal remainAmount;

    @JsonProperty("O")
    private long createTime;

    @JsonProperty("S")
    private int tradeType; // 1: buy, 2: sell

    @JsonProperty("V")
    private BigDecimal remainQuantity;

    @JsonProperty("a")
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
    private BigDecimal price;

    @JsonProperty("s")
    private int status; // 1: New order, 2: Filled, 3: Partially filled, etc.

    @JsonProperty("v")
    private BigDecimal quantity;

    @JsonProperty("ap")
    private BigDecimal avgPrice;

    @JsonProperty("cv")
    private BigDecimal cumulativeQuantity;

    @JsonProperty("ca")
    private BigDecimal cumulativeAmount;

    @JsonProperty("t")
    private long eventTime;

    @JsonProperty("s")
    private String symbol;
}
