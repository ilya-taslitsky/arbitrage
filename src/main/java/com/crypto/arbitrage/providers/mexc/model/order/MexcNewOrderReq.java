package com.crypto.arbitrage.providers.mexc.model.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import velox.api.layer1.data.OrderSendParameters;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MexcNewOrderReq implements OrderSendParameters {
  @NotBlank(message = "Symbol is required")
  private String symbol;

  @NotNull(message = "Order side is required")
  private MexcOrderSide side;

  @NotNull(message = "Order type is required")
  private MexcOrderType type;

  private int quantity;
  private int quoteOrderQty;
  private int price;
  private String newClientOrderId;
  private long recvWindow;
  private long timestamp;
  private String signature;
}
