package com.crypto.arbitrage.providers.mexc.model.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import velox.api.layer1.data.OrderSendParameters;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MexcNewOrderReq implements OrderSendParameters {
    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Order side is required")
    private MexcOrderSide side;

    @NotNull(message = "Order type is required")
    private MexcOrderType type;

    @DecimalMin(value = "0.0", inclusive = false, message = "Quantity must be positive")
    private BigDecimal quantity;

    @DecimalMin(value = "0.0", inclusive = false, message = "Quote order quantity must be positive")
    private BigDecimal quoteOrderQty;

    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
    private BigDecimal price;

    private String newClientOrderId;
    private Long recvWindow;
    private Long timestamp;
    private String signature;
}


