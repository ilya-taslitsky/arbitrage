package com.crypto.arbitrage.providers.orc.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents a tick array in a Whirlpool. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcTickArray {
  private String address;
  private int startTickIndex;
  private OrcTick[] ticks;
  private boolean initialized;
}
