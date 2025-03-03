package com.crypto.arbitrage.providers.orc.models;

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a quote for increasing or decreasing liquidity in a whirlpool position. Equivalent to
 * IncreaseLiquidityQuote or DecreaseLiquidityQuote in the Rust SDK.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcLiquidityQuote {
  // Liquidity delta (amount of liquidity to add or remove)
  private BigInteger liquidityDelta;

  // Estimated amount of token A required or received
  private BigInteger tokenEstA;

  // Estimated amount of token B required or received
  private BigInteger tokenEstB;

  // Maximum amount of token A to spend (when increasing liquidity)
  // or minimum amount of token A to receive (when decreasing liquidity)
  private BigInteger tokenMaxA;

  // Maximum amount of token B to spend (when increasing liquidity)
  // or minimum amount of token B to receive (when decreasing liquidity)
  private BigInteger tokenMinB;
}
