package com.crypto.arbitrage.providers.mexc.controller;

import com.crypto.arbitrage.providers.mexc.model.order.CancelOrderReq;
import com.crypto.arbitrage.providers.mexc.model.order.CancelOrderResp;
import com.crypto.arbitrage.providers.mexc.model.order.NewOrderReq;
import com.crypto.arbitrage.providers.mexc.model.order.NewOrderResp;
import com.crypto.arbitrage.providers.mexc.service.MexcOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MexcTestController {

    private final static String ACCOUNT_URL = "/account";
    private final static String ORDER_URL = "/order";
    private final static String WITHDRAW_URL = "/withdraw";

    private final MexcOrderService mexcOrderService;
//    private final MexcWithdrawService mexcWithdrawService;
//    private final MexcAccountServiceImpl mexcAccountService;
//
//    @GetMapping(ACCOUNT_URL)
//    public ResponseEntity<?> accountInfo(){
//        return ResponseEntity.ok(mexcAccountService.getAccountInfo());
//    }

    @PostMapping(ORDER_URL)
    public ResponseEntity<?> createOrder(@Valid @ModelAttribute NewOrderReq req) {
        NewOrderResp order = mexcOrderService.sendOrder(req);
        return ResponseEntity.ok(order);
    }

    @DeleteMapping(ORDER_URL)
    public ResponseEntity<?> cancelOrder(@Valid @ModelAttribute CancelOrderReq req) {
        CancelOrderResp cancelOrderResponse = mexcOrderService.cancelOrder(req);
        if (cancelOrderResponse != null) {
            return ResponseEntity.ok(cancelOrderResponse);
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

//    @PostMapping(WITHDRAW_URL)
//    public ResponseEntity<?> withdraw(@Valid @ModelAttribute WithdrawRequest req) {
//        WithdrawResponse withdraw = mexcWithdrawService.withdraw(req);
//        if (withdraw != null) {
//            return ResponseEntity.ok(withdraw);
//        } else {
//            return ResponseEntity.badRequest().build();
//        }
//    }
}
