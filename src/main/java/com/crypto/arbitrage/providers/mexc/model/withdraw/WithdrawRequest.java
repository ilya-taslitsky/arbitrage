package com.crypto.arbitrage.providers.mexc.model.withdraw;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WithdrawRequest {
    private String coin;
    private String withdrawOrderId;
    private String netWork;
    private String address;
    private String memo;
    private BigDecimal amount;
    private String remark;
    private Long recvWindow;
    private Long timestamp;
    private String signature;
}

