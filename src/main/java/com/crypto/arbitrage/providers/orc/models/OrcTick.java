package com.crypto.arbitrage.providers.orc.models;

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Individual tick in a tick array. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcTick {
  private boolean initialized;
  private BigInteger liquidityNet;
  private BigInteger liquidityGross;
  private BigInteger feeGrowthOutsideA;
  private BigInteger feeGrowthOutsideB;
  private BigInteger[] rewardGrowthsOutside;
}
