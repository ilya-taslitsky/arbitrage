package com.crypto.arbitrage.providers.orc.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrcPeriodStats {
  private String volume;
  private String fees;
  private String rewards;
}
