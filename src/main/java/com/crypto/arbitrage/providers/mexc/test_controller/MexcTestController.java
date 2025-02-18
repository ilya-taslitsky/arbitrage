package com.crypto.arbitrage.providers.mexc.test_controller;

import com.crypto.arbitrage.providers.mexc.model.order.MexcCancelOrderReq;
import com.crypto.arbitrage.providers.mexc.model.order.MexcCancelOrderResp;
import com.crypto.arbitrage.providers.mexc.model.order.MexcNewOrderReq;
import com.crypto.arbitrage.providers.mexc.model.order.MexcNewOrderResp;
import com.crypto.arbitrage.providers.mexc.service.MexcOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mexc")
@RequiredArgsConstructor
public class MexcTestController {

  private static final String ORDER_URL = "/order";
  private static final String ACCOUNT_URL = "/account";
  private static final String WITHDRAW_URL = "/withdraw";

  private final MexcOrderService mexcOrderService;

  @PostMapping(ORDER_URL)
  public ResponseEntity<?> createOrder(@Valid @ModelAttribute MexcNewOrderReq req) {
    MexcNewOrderResp order = mexcOrderService.sendOrder(req);
    return ResponseEntity.ok(order);
  }

  @DeleteMapping(ORDER_URL)
  public ResponseEntity<?> cancelOrder(@Valid @ModelAttribute MexcCancelOrderReq req) {
    MexcCancelOrderResp mexcCancelOrderResponse = mexcOrderService.cancelOrder(req);
    if (mexcCancelOrderResponse != null) {
      return ResponseEntity.ok(mexcCancelOrderResponse);
    } else {
      return ResponseEntity.badRequest().build();
    }
  }
}
