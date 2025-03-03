package com.crypto.arbitrage.providers.orc.models;

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Quote for a token swap. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcSwapQuote {
  private BigInteger tokenIn; // Amount of input token
  private BigInteger estimatedAmountIn; // Estimated input amount (after fee)
  private BigInteger tokenMaxIn; // Maximum input amount (with slippage)
  private BigInteger estimatedAmountOut; // Estimated output amount
  private BigInteger tokenMinOut; // Minimum output amount (with slippage)
  private BigInteger liquidityDelta; // Liquidity change
  private BigInteger fee; // Fee amount
}
