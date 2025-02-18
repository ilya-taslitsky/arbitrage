package com.crypto.arbitrage.providers.mexc.common;

import com.crypto.arbitrage.providers.mexc.model.order.MexcNewOrderReq;
import com.crypto.arbitrage.providers.mexc.model.order.MexcOrderSide;
import com.crypto.arbitrage.providers.mexc.model.order.MexcOrderType;
import org.springframework.lang.NonNull;
import velox.api.layer1.data.SimpleOrderSendParameters;

public class MexcMapper {

  public static MexcNewOrderReq toMexcNewOrderReq(@NonNull SimpleOrderSendParameters req) {
    return MexcNewOrderReq.builder()
        .symbol(req.alias)
        .side(req.isBuy ? MexcOrderSide.BUY : MexcOrderSide.SELL)
        .type(MexcOrderType.MARKET)
        .quantity(req.size)
        .build();
  }
}
