package com.crypto.arbitrage.providers.orc.model.transaction;

import java.math.BigInteger;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrcWhirlpool {
  public String whirlpoolsConfig;
  public byte whirlpoolBump;
  public int tickSpacing;
  public byte[] tickSpacingSeed;
  public int feeRate;
  public int protocolFeeRate;
  public BigInteger liquidity;
  public BigInteger sqrtPrice;
  public int tickCurrentIndex;
  public BigInteger protocolFeeOwedA;
  public BigInteger protocolFeeOwedB;
  public String tokenMintA;
  public String tokenVaultA;
  public BigInteger feeGrowthGlobalA;
  public String tokenMintB;
  public String tokenVaultB;
  public BigInteger feeGrowthGlobalB;
}
