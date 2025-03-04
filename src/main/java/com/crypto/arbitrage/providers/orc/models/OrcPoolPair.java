package com.crypto.arbitrage.providers.orc.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents a pair of pools that can be used for arbitrage. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcPoolPair {
  private String firstPool;
  private String secondPool;

  // Optional fields to store additional data about the pools
  private String sharedToken;
  private boolean compatible;
}
