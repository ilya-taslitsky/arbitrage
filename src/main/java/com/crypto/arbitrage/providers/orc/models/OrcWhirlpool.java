package com.crypto.arbitrage.providers.orc.models;

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents a Whirlpool - a concentrated liquidity pool in Orca. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcWhirlpool {
  private String address;
  private String whirlpoolsConfig;
  private byte whirlpoolBump;
  private int tickSpacing;
  private int feeRate;
  private int protocolFeeRate;
  private BigInteger liquidity;
  private BigInteger sqrtPrice;
  private int tickCurrentIndex;
  private BigInteger protocolFeeOwedA;
  private BigInteger protocolFeeOwedB;
  private String tokenMintA;
  private String tokenVaultA;
  private BigInteger feeGrowthGlobalA;
  private String tokenMintB;
  private String tokenVaultB;
  private BigInteger feeGrowthGlobalB;
  private byte[] rewardInfos; // Raw bytes for reward infos (parsed only when needed)
}
