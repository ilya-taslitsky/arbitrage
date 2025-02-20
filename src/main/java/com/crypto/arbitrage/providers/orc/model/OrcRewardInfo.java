package com.crypto.arbitrage.providers.orc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrcRewardInfo {
  private String mint;
  private String vault;
  private String authority;

  @JsonProperty("emissions_per_second_x64")
  private String emissionsPerSecondX64;

  @JsonProperty("growth_global_x64")
  private String growthGlobalX64;

  private boolean active;
}
