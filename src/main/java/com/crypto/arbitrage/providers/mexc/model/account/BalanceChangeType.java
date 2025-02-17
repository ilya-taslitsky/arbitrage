package com.crypto.arbitrage.providers.mexc.model.account;

public enum BalanceChangeType {
    WITHDRAW,
    WITHDRAW_FEE,
    DEPOSIT,
    DEPOSIT_FEE,
    ENTRUST,
    ENTRUST_PLACE,
    ENTRUST_CANCEL,
    TRADE_FEE,
    ENTRUST_UNFROZEN,
    SUGAR,
    ETF_INDEX;

    public static BalanceChangeType fromString(String value) {
        for (BalanceChangeType type : BalanceChangeType.values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown balance change type: " + value);
    }
}
