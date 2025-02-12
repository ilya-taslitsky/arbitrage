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
