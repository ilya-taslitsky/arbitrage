package com.crypto.arbitrage.providers.mexc.model.account;

import com.crypto.arbitrage.providers.mexc.model.MexcData;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@Data
public class MexcAccountBalance implements MexcData {

    @JsonProperty("c")
    private String channel;

    @JsonProperty("d")
    private AccountUpdates accountUpdates;

    @JsonProperty("t")
    private long eventTime;


    @Data
    public static class AccountUpdates {

        @JsonProperty("a")
        private String asset;

        @JsonProperty("c")
        private long changeTime;

        @JsonProperty("f")
        private double freeBalance;

        @JsonProperty("fd")
        private double freeChangedAmount;

        @JsonProperty("l")
        private double frozenAmount;

        @JsonProperty("ld")
        private double frozenChangedAmount;

        @JsonProperty("o")
        @JsonDeserialize(using = BalanceChangeTypeDeserializer.class)
        private BalanceChangeType changedType;
    }
}
