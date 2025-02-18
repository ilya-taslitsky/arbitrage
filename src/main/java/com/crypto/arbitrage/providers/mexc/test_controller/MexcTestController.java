package com.crypto.arbitrage.providers.mexc.test_controller;

import com.crypto.arbitrage.providers.mexc.model.order.MexcCancelOrderReq;
import com.crypto.arbitrage.providers.mexc.model.order.MexcNewOrderReq;
import com.crypto.arbitrage.providers.mexc.service.MexcOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
  @ResponseStatus(code = HttpStatus.CREATED)
  public void createOrder(@Valid @ModelAttribute MexcNewOrderReq req) {
    mexcOrderService.sendOrder(req);
  }

  @DeleteMapping(ORDER_URL)
  @ResponseStatus(code = HttpStatus.NO_CONTENT)
  public void cancelOrder(@Valid @ModelAttribute MexcCancelOrderReq req) {
    mexcOrderService.cancelOrder(req);
  }
}
