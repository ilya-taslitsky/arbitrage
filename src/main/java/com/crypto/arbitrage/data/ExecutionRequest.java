package com.crypto.arbitrage.data;

import lombok.Data;

@Data
public class ExecutionRequest {
    private final String alias;
    private final double executionPrice;
    private final int lastTradeSize;
    private final boolean isBuyTrade;
    private final boolean isTriggeredByBbo;

    public ExecutionRequest(String alias, double executionPrice, int lastTradeSize, boolean isBuyTrade, boolean isTriggeredByBbo) {
        this.alias = alias;
        this.executionPrice = executionPrice;
        this.lastTradeSize = lastTradeSize;
        this.isBuyTrade = isBuyTrade;
        this.isTriggeredByBbo = isTriggeredByBbo;
    }
}