package com.crypto.arbitrage.providers.mexc.model.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Игнорировать неизвестные поля
public class MexcCancelOrderResp {
    private String symbol;

    @JsonProperty("origClientOrderId")
    private String origClientOrderId;

    @JsonProperty("orderId")
    private String orderId; // Используем String для универсальности

    @JsonProperty("clientOrderId")
    private String clientOrderId;

    private String price;

    @JsonProperty("origQty")
    private String origQty;

    private String executedQty;

    private String cummulativeQuoteQty;

    private MexcOrderStatus status;

    private MexcTimeInForce mexcTimeInForce;

    private MexcOrderType type;

    private MexcOrderSide side;
}

