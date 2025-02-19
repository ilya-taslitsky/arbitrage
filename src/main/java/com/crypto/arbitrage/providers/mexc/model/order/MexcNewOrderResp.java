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
  private String orderId;
  private int orderListId;
  private String price;
  private String origQty;
  private MexcOrderType type;
  private MexcOrderSide side;
  private long transactTime;
}
