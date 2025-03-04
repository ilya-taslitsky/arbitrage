package com.crypto.arbitrage.providers.orc.utils;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.*;
import com.crypto.arbitrage.providers.orc.services.OrcAccountFetcher;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Utility class for debugging Orca Whirlpool operations. Provides methods to diagnose issues with
 * swaps, quotes, and mathematical calculations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrcDebugUtility {

  private final OrcAccountFetcher accountFetcher;

  /**
   * Performs detailed diagnostic on a pool to identify potential issues.
   *
   * @param poolAddress The address of the Whirlpool to diagnose
   * @return A diagnostic report as a String
   */
  public String diagnosePool(String poolAddress) {
    StringBuilder report = new StringBuilder();
    report.append("=== Whirlpool Diagnostic Report ===\n");

    try {
      // Fetch the pool
      OrcWhirlpool pool = accountFetcher.fetchWhirlpool(poolAddress);

      // Basic pool info
      report.append("Pool Address: ").append(poolAddress).append("\n");
      report.append("Token A: ").append(pool.getTokenMintA()).append("\n");
      report.append("Token B: ").append(pool.getTokenMintB()).append("\n");
      report.append("Liquidity: ").append(pool.getLiquidity()).append("\n");

      // Decode token names for known tokens
      String tokenAName = decodeTokenName(pool.getTokenMintA());
      String tokenBName = decodeTokenName(pool.getTokenMintB());
      report.append("Token A Name: ").append(tokenAName).append("\n");
      report.append("Token B Name: ").append(tokenBName).append("\n");

      // Pool parameters
      report.append("Fee Rate: ").append(pool.getFeeRate() / 100.0).append(" bps\n");
      report.append("Tick Spacing: ").append(pool.getTickSpacing()).append("\n");
      report.append("Current Tick Index: ").append(pool.getTickCurrentIndex()).append("\n");

      // Price info
      int decimalsA = getTokenDecimals(pool.getTokenMintA());
      int decimalsB = getTokenDecimals(pool.getTokenMintB());
      report.append("Token A Decimals: ").append(decimalsA).append("\n");
      report.append("Token B Decimals: ").append(decimalsB).append("\n");

      BigInteger sqrtPriceX64 = pool.getSqrtPrice();
      report.append("Sqrt Price (X64): ").append(sqrtPriceX64).append("\n");

      double price = OrcMathUtils.sqrtPriceX64ToPrice(sqrtPriceX64, decimalsA, decimalsB);
      report
          .append("Price (")
          .append(tokenBName)
          .append(" per ")
          .append(tokenAName)
          .append("): ")
          .append(formatDouble(price))
          .append("\n");

      // Check for potential issues
      report.append("\n=== Potential Issues ===\n");

      // Check liquidity
      if (pool.getLiquidity().compareTo(BigInteger.valueOf(1000000)) < 0) {
        report.append("⚠️ WARNING: Very low liquidity. Swaps may not work as expected.\n");
      }

      // Check sqrt price boundaries
      if (sqrtPriceX64.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
        report.append("⚠️ ERROR: Sqrt price below minimum allowed value!\n");
      } else if (sqrtPriceX64.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
        report.append("⚠️ ERROR: Sqrt price above maximum allowed value!\n");
      }

      // Check for transfer fees
      OrcTransferFee transferFeeA = accountFetcher.extractTransferFee(pool.getTokenMintA());
      OrcTransferFee transferFeeB = accountFetcher.extractTransferFee(pool.getTokenMintB());

      if (transferFeeA != null) {
        report
            .append("⚠️ NOTE: Token A has transfer fees: ")
            .append(transferFeeA.getFeeBasisPoints() / 100.0)
            .append(" bps\n");
      }

      if (transferFeeB != null) {
        report
            .append("⚠️ NOTE: Token B has transfer fees: ")
            .append(transferFeeB.getFeeBasisPoints() / 100.0)
            .append(" bps\n");
      }

      // Add minimum viable amount info
      BigInteger minAmountA = calculateMinimumViableAmount(pool, pool.getTokenMintA());
      BigInteger minAmountB = calculateMinimumViableAmount(pool, pool.getTokenMintB());

      BigDecimal minAmountAFormatted =
          new BigDecimal(minAmountA)
              .divide(BigDecimal.TEN.pow(decimalsA), decimalsA, RoundingMode.HALF_UP);
      BigDecimal minAmountBFormatted =
          new BigDecimal(minAmountB)
              .divide(BigDecimal.TEN.pow(decimalsB), decimalsB, RoundingMode.HALF_UP);

      report.append("\n=== Recommended Minimum Swap Amounts ===\n");
      report
          .append("Minimum ")
          .append(tokenAName)
          .append(": ")
          .append(minAmountAFormatted)
          .append("\n");
      report
          .append("Minimum ")
          .append(tokenBName)
          .append(": ")
          .append(minAmountBFormatted)
          .append("\n");

      // Add sample quote examples
      report.append("\n=== Sample Quote Analysis ===\n");

      // Sample A to B swap quote
      BigInteger sampleAmountA = minAmountA.multiply(BigInteger.valueOf(10)); // 10x minimum
      OrcSwapQuote quoteAtoB = calculateSampleQuote(sampleAmountA, pool, true, false);

      BigDecimal sampleAmountAFormatted =
          new BigDecimal(sampleAmountA)
              .divide(BigDecimal.TEN.pow(decimalsA), decimalsA, RoundingMode.HALF_UP);
      BigDecimal outputBFormatted =
          new BigDecimal(quoteAtoB.getEstimatedAmountOut())
              .divide(BigDecimal.TEN.pow(decimalsB), decimalsB, RoundingMode.HALF_UP);

      report.append(tokenAName).append(" to ").append(tokenBName).append(" swap:\n");
      report
          .append("  Input: ")
          .append(sampleAmountAFormatted)
          .append(" ")
          .append(tokenAName)
          .append("\n");
      report
          .append("  Output: ")
          .append(outputBFormatted)
          .append(" ")
          .append(tokenBName)
          .append("\n");

      // Sample B to A swap quote
      BigInteger sampleAmountB = minAmountB.multiply(BigInteger.valueOf(10)); // 10x minimum
      OrcSwapQuote quoteBtoA = calculateSampleQuote(sampleAmountB, pool, false, false);

      BigDecimal sampleAmountBFormatted =
          new BigDecimal(sampleAmountB)
              .divide(BigDecimal.TEN.pow(decimalsB), decimalsB, RoundingMode.HALF_UP);
      BigDecimal outputAFormatted =
          new BigDecimal(quoteBtoA.getEstimatedAmountOut())
              .divide(BigDecimal.TEN.pow(decimalsA), decimalsA, RoundingMode.HALF_UP);

      report.append(tokenBName).append(" to ").append(tokenAName).append(" swap:\n");
      report
          .append("  Input: ")
          .append(sampleAmountBFormatted)
          .append(" ")
          .append(tokenBName)
          .append("\n");
      report
          .append("  Output: ")
          .append(outputAFormatted)
          .append(" ")
          .append(tokenAName)
          .append("\n");

    } catch (Exception e) {
      report.append("ERROR: Failed to diagnose pool: ").append(e.getMessage()).append("\n");
      log.error("Error diagnosing pool {}: {}", poolAddress, e.getMessage(), e);
    }

    return report.toString();
  }

  /** Tests a sample swap quote calculation for diagnostics. */
  private OrcSwapQuote calculateSampleQuote(
      BigInteger amountIn, OrcWhirlpool pool, boolean aToB, boolean exactOut) {

    try {
      // Apply fee rate to input amount
      BigInteger amountInAfterFee = OrcMathUtils.applyFeeRate(amountIn, pool.getFeeRate());

      // Calculate next sqrt price
      BigInteger currentSqrtPrice = pool.getSqrtPrice();
      BigInteger nextSqrtPrice;
      BigInteger amountOut;

      if (aToB) {
        // A to B swap (price goes down)
        nextSqrtPrice =
            OrcMathUtils.getNextSqrtPriceFromTokenAInput(
                currentSqrtPrice, pool.getLiquidity(), amountInAfterFee, true);

        // Ensure the price doesn't go below minimum
        if (nextSqrtPrice.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
          nextSqrtPrice = OrcConstants.MIN_SQRT_PRICE;
        }

        amountOut =
            OrcMathUtils.getTokenBDelta(pool.getLiquidity(), nextSqrtPrice, currentSqrtPrice);
      } else {
        // B to A swap (price goes up)
        nextSqrtPrice =
            OrcMathUtils.getNextSqrtPriceFromTokenBInput(
                currentSqrtPrice, pool.getLiquidity(), amountInAfterFee, true);

        // Ensure the price doesn't go above maximum
        if (nextSqrtPrice.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
          nextSqrtPrice = OrcConstants.MAX_SQRT_PRICE;
        }

        amountOut =
            OrcMathUtils.getTokenADelta(pool.getLiquidity(), currentSqrtPrice, nextSqrtPrice);
      }

      // Check for zero output
      if (amountOut.equals(BigInteger.ZERO)) {
        amountOut = BigInteger.ONE;
      }

      // Calculate minimum output with 1% slippage
      BigInteger minAmountOut =
          amountOut.multiply(BigInteger.valueOf(9900)).divide(BigInteger.valueOf(10000));

      // Ensure minimum output is at least 1
      if (minAmountOut.equals(BigInteger.ZERO)) {
        minAmountOut = BigInteger.ONE;
      }

      return OrcSwapQuote.builder()
          .tokenIn(amountIn)
          .estimatedAmountIn(amountIn)
          .tokenMaxIn(amountIn)
          .estimatedAmountOut(amountOut)
          .tokenMinOut(minAmountOut)
          .build();

    } catch (Exception e) {
      log.error("Error calculating sample quote: {}", e.getMessage(), e);
      // Return fallback quote with zero output to indicate error
      return OrcSwapQuote.builder()
          .tokenIn(amountIn)
          .estimatedAmountIn(amountIn)
          .tokenMaxIn(amountIn)
          .estimatedAmountOut(BigInteger.ZERO)
          .tokenMinOut(BigInteger.ZERO)
          .build();
    }
  }

  /** Calculates the minimum viable amount for a swap in a given pool. */
  private BigInteger calculateMinimumViableAmount(OrcWhirlpool pool, String inputToken) {
    // Get token decimals
    int decimalsA = getTokenDecimals(pool.getTokenMintA());
    int decimalsB = getTokenDecimals(pool.getTokenMintB());

    // Determine if input is token A or B
    boolean isTokenA = pool.getTokenMintA().equals(inputToken);
    int inputDecimals = isTokenA ? decimalsA : decimalsB;

    // For SOL/USDC pools, use known good minimums
    if ((pool.getTokenMintA().equals(OrcConstants.WSOL_MINT)
            && pool.getTokenMintB().equals(OrcConstants.USDC_MINT))
        || (pool.getTokenMintA().equals(OrcConstants.USDC_MINT)
            && pool.getTokenMintB().equals(OrcConstants.WSOL_MINT))) {

      if (inputToken.equals(OrcConstants.WSOL_MINT)) {
        // For SOL: 0.01 SOL (10^7 lamports) is typically safe
        return BigInteger.valueOf(10_000_000);
      } else if (inputToken.equals(OrcConstants.USDC_MINT)) {
        // For USDC: 1 USDC (10^6 units) is typically safe
        return BigInteger.valueOf(1_000_000);
      }
    }

    // Base minimum on token decimals and pool liquidity
    BigInteger baseMinimum;

    // Higher liquidity pools can handle smaller amounts but still need minimums
    if (pool.getLiquidity().compareTo(BigInteger.valueOf(1_000_000_000L)) > 0) {
      // For high liquidity pools: 0.001 of the token
      baseMinimum = BigInteger.TEN.pow(Math.max(1, inputDecimals - 3));
    } else {
      // For lower liquidity pools: 0.01 of the token
      baseMinimum = BigInteger.TEN.pow(Math.max(1, inputDecimals - 2));
    }

    return baseMinimum;
  }

  /** Decode token names for known token mints. */
  private String decodeTokenName(String tokenMint) {
    if (OrcConstants.USDC_MINT.equals(tokenMint)) {
      return "USDC";
    } else if (OrcConstants.USDT_MINT.equals(tokenMint)) {
      return "USDT";
    } else if (OrcConstants.WSOL_MINT.equals(tokenMint)) {
      return "SOL";
    }
    return "Unknown Token";
  }

  /** Helper to get token decimals for common tokens */
  private int getTokenDecimals(String tokenMint) {
    // Common token decimals
    if (OrcConstants.USDC_MINT.equals(tokenMint) || OrcConstants.USDT_MINT.equals(tokenMint)) {
      return 6; // USDC and USDT have 6 decimals
    } else if (OrcConstants.WSOL_MINT.equals(tokenMint)) {
      return 9; // SOL has 9 decimals
    }

    // Default for unknown tokens
    return 9;
  }

  /** Format a double value with appropriate decimal places. */
  private String formatDouble(double value) {
    if (value < 0.001) {
      return String.format("%.8f", value);
    } else if (value < 1) {
      return String.format("%.6f", value);
    } else if (value < 1000) {
      return String.format("%.4f", value);
    } else {
      return String.format("%.2f", value);
    }
  }

  /**
   * Analyzes an error message and provides suggested fixes.
   *
   * @param errorMessage The error message to analyze
   * @return Suggested fixes as a String
   */
  public String analyzeError(String errorMessage) {
    StringBuilder suggestions = new StringBuilder();
    suggestions.append("Error Analysis and Suggested Fixes:\n");

    if (errorMessage.contains("Custom[error=101]")) {
      suggestions.append("Error 101 - Zero Tradable Amount:\n");
      suggestions.append("• The swap amount is too small to produce a non-zero result\n");
      suggestions.append("• Try increasing your swap amount significantly\n");
      suggestions.append("• For USDC, use at least 1 USDC (1,000,000 units)\n");
      suggestions.append("• For SOL, use at least 0.01 SOL (10,000,000 lamports)\n");
      suggestions.append("• Check pool liquidity to ensure it's sufficient\n");

    } else if (errorMessage.contains("Custom[error=6]")) {
      suggestions.append("Error 6 - Slippage Tolerance Exceeded:\n");
      suggestions.append("• The price moved more than your slippage tolerance allows\n");
      suggestions.append("• Try increasing your slippage tolerance\n");
      suggestions.append("• Default slippage is 1% (100 bps), try 2-3% for volatile pools\n");

    } else if (errorMessage.contains("InvalidAccountData")) {
      suggestions.append("Invalid Account Data Error:\n");
      suggestions.append("• One of the accounts in the transaction is invalid\n");
      suggestions.append("• Check that all account addresses are correct\n");
      suggestions.append("• Token accounts may need to be created first\n");

    } else if (errorMessage.contains("arithmetic operation overflow")) {
      suggestions.append("Arithmetic Overflow Error:\n");
      suggestions.append("• Mathematical calculation overflowed during swap simulation\n");
      suggestions.append("• Try a smaller swap amount\n");
      suggestions.append("• The pool may have extreme price or liquidity values\n");

    } else {
      suggestions.append("Unknown Error:\n");
      suggestions.append("• Check that you have sufficient balance to cover the swap\n");
      suggestions.append("• Verify the pool exists and has liquidity\n");
      suggestions.append("• Try using the diagnosePool method for more information\n");
    }

    return suggestions.toString();
  }
}
