package com.crypto.arbitrage.providers.mexc.model.order;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MexcNewOrderResp {
    private String symbol;
    private String origClientOrderId;
    private String orderId;
    private String clientOrderId;
    private String price;
    private String origQty; // "origOty" is assumed to be a typo; using origQty here.
    private String executedQty;
    private String cummulativeQuoteQty;
    private String status;
    private String timeInForce;
    private String type;
    private String side;
}

