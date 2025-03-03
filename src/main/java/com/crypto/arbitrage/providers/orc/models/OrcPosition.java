package com.crypto.arbitrage.providers.orc.models;

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents a position in a Whirlpool. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcPosition {
  private String address;
  private String positionMint;
  private String whirlpool;
  private String owner;
  private int tickLowerIndex;
  private int tickUpperIndex;
  private BigInteger liquidity;
  private BigInteger feeOwedA;
  private BigInteger feeOwedB;
  private BigInteger[] rewardOwed; // Array of reward amounts owed
}
