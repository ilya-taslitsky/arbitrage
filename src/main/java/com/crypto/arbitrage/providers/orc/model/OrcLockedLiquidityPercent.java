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
public class OrcLockedLiquidityPercent {
  private String name;

  @JsonProperty("locked_percentage")
  private String lockedPercentage;
}
