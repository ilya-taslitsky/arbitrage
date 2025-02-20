package com.crypto.arbitrage.providers.orc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrcPoolData {
  private String address;
  private String tokenMintA;
  private String tokenMintB;
  private int feeRate;
  private int protocolFeeRate;
  private int tickSpacing;
  private String updatedAt;
  private String price;
  private boolean hasWarning;
  private String poolType;
  private String poolsConfig;
  private List<Integer> poolBump;
  private List<Integer> tickSpacingSpeed;
  private int tickCurrentIndex;
  private String sqrtPrice;
  private String liquidity;
  private String protocolFeeOwedA;
  private String protocolFeeOwedB;
  private String tokenVaultA;
  private String tokenVaultB;
  private String tokenBalanceA;
  private String tokenBalanceB;
  private String feeGrowthGlobalA;
  private String feeGrowthGlobalB;
  private String rewardLastUpdatedTimestamp;
  private OrcTokenInfo tokenA;
  private OrcTokenInfo tokenB;
  private String tvlUsdc;
  private OrcStat stats;
  private List<OrcRewardInfo> rewardInfos;

  @JsonProperty("locked_liquidity_percent")
  private List<OrcLockedLiquidityPercent> lockedLiquidityPercent;
}
