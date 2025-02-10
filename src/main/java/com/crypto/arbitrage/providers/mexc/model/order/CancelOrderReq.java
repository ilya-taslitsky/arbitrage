package com.crypto.arbitrage.providers.mexc.model.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CancelOrderReq {

    @NotBlank(message = "Symbol is required")
    @Pattern(regexp = "^[A-Z]+$", message = "Symbol must consist of uppercase letters only")
    private String symbol;

    @NotBlank(message = "Order ID is required")
    @Pattern(regexp = "^.*__(?=\\d+$)\\d+$", message = "Order ID must consist of digits only")
    private String orderId;
    private String origClientOrderId;
}

