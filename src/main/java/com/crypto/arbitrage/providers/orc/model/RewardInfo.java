package com.crypto.arbitrage.providers.orc.model;

import lombok.Data;

import java.math.BigInteger;

@Data
public class RewardInfo {
    private String mint;
    private String vault;
    private BigInteger growthGlobal;

    // Getters and Setters
}