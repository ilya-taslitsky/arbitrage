package com.crypto.arbitrage.providers.mexc;

import lombok.Data;

@Data
public class MexTradeData {
    private String symbolName;
    private long time;
    private String accountId;
    private double size;
    private double price;
    private String side;  // buy or sell
}
