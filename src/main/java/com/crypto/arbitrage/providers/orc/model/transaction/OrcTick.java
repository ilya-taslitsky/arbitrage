package com.crypto.arbitrage.providers.orc.model.transaction;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrcTick {
  public boolean initialized;
  public String liquidityNet;
  public String liquidityGross;
  public String feeGrowthOutsideA;
  public String feeGrowthOutsideB;
  public String[] rewardGrowthsOutside;
}
