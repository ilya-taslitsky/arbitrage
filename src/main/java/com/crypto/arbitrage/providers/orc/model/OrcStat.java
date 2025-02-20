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
public class OrcStat {
  @JsonProperty("24h")
  private OrcPeriodStats day24;

  @JsonProperty("7d")
  private OrcPeriodStats day7;

  @JsonProperty("30d")
  private OrcPeriodStats day30;
}
