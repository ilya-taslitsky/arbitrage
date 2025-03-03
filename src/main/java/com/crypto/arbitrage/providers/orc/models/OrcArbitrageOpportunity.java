package com.crypto.arbitrage.providers.orc.models;

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents an arbitrage opportunity between pools. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcArbitrageOpportunity {
  private String firstPoolAddress;
  private String secondPoolAddress;
  private BigInteger inputAmount;
  private String inputToken;
  private String firstPoolDirection;
  private BigInteger intermediateAmount;
  private String intermediateToken;
  private String secondPoolDirection;
  private BigInteger outputAmount;
  private BigInteger profitAmount;
  private int profitBasisPoints;
}
