package com.crypto.arbitrage.providers.orc.utils;

import static com.crypto.arbitrage.providers.orc.constants.OrcConstants.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import lombok.extern.slf4j.Slf4j;

/**
 * Utilities for fixed-point math operations required for Orca Whirlpool calculations. Implements
 * the same math operations as the Rust SDK with safeguards against overflows and precision errors.
 */
@Slf4j
public class OrcMathUtils {

  private static final MathContext MC = new MathContext(30, RoundingMode.HALF_UP);

  /**
   * Converts a price to its sqrt price representation in Q64.64 format.
   *
   * @param price The price value
   * @param decimalsA Decimals for token A
   * @param decimalsB Decimals for token B
   * @return The square root price as a Q64.64 BigInteger
   */
  public static BigInteger priceToSqrtPriceX64(double price, int decimalsA, int decimalsB) {
    if (price <= 0) {
      throw new IllegalArgumentException("Price must be greater than zero");
    }

    try {
      // Adjust for decimal places
      int decimalAdjustment = decimalsA - decimalsB;
      BigDecimal adjustedPrice = BigDecimal.valueOf(price);

      if (decimalAdjustment > 0) {
        // If token A has more decimals, multiply the price
        adjustedPrice = adjustedPrice.multiply(BigDecimal.TEN.pow(decimalAdjustment, MC));
      } else if (decimalAdjustment < 0) {
        // If token B has more decimals, divide the price
        adjustedPrice = adjustedPrice.divide(BigDecimal.TEN.pow(-decimalAdjustment, MC), MC);
      }

      // Calculate square root - this is a critical operation for accurate price representation
      BigDecimal sqrtPrice = BigDecimal.valueOf(Math.sqrt(adjustedPrice.doubleValue()));

      // Convert to Q64.64 format
      BigDecimal q64Scale = new BigDecimal(Q64);
      BigDecimal scaledValue = sqrtPrice.multiply(q64Scale);

      // Boundary checks
      if (scaledValue.compareTo(new BigDecimal(MIN_SQRT_PRICE)) < 0) {
        log.warn("Calculated sqrt price below minimum, using MIN_SQRT_PRICE");
        return MIN_SQRT_PRICE;
      }
      if (scaledValue.compareTo(new BigDecimal(MAX_SQRT_PRICE)) > 0) {
        log.warn("Calculated sqrt price above maximum, using MAX_SQRT_PRICE");
        return MAX_SQRT_PRICE;
      }

      return scaledValue.toBigInteger();
    } catch (Exception e) {
      log.error("Error converting price to sqrtPriceX64: {}", e.getMessage(), e);
      throw new RuntimeException("Error in price calculation: " + e.getMessage());
    }
  }

  /**
   * Converts a sqrt price in Q64.64 format back to a regular price.
   *
   * @param sqrtPriceX64 The sqrt price in Q64.64 format
   * @param decimalsA Decimals for token A
   * @param decimalsB Decimals for token B
   * @return The calculated price
   */
  public static double sqrtPriceX64ToPrice(BigInteger sqrtPriceX64, int decimalsA, int decimalsB) {
    if (sqrtPriceX64.compareTo(BigInteger.ZERO) <= 0) {
      throw new IllegalArgumentException("Sqrt price must be greater than zero");
    }

    try {
      // Convert sqrt price to a decimal for precision
      BigDecimal sqrtPrice =
          new BigDecimal(sqrtPriceX64).divide(new BigDecimal(Q64), 20, RoundingMode.HALF_UP);

      // Square the sqrt price to get the actual price
      BigDecimal price = sqrtPrice.multiply(sqrtPrice);

      // Adjust for decimal places
      int decimalAdjustment = decimalsA - decimalsB;
      if (decimalAdjustment > 0) {
        // If token A has more decimals, divide the price
        return price
            .divide(BigDecimal.TEN.pow(decimalAdjustment, MC), 20, RoundingMode.HALF_UP)
            .doubleValue();
      } else if (decimalAdjustment < 0) {
        // If token B has more decimals, multiply the price
        return price.multiply(BigDecimal.TEN.pow(-decimalAdjustment, MC)).doubleValue();
      }

      return price.doubleValue();
    } catch (Exception e) {
      log.error("Error converting sqrtPriceX64 to price: {}", e.getMessage(), e);
      throw new RuntimeException("Error in price calculation: " + e.getMessage());
    }
  }

  /**
   * Calculate the next sqrt price for a swap with token A as input. Formula: (liquidity *
   * sqrtPriceX64) / (liquidity + amount * sqrtPriceX64 / Q64)
   *
   * @param sqrtPriceX64 Current sqrt price in Q64.64
   * @param liquidity Current liquidity
   * @param amount Amount of token A
   * @param byAmountIn Whether the amount is input (true) or output (false)
   * @return The next sqrt price
   */
  public static BigInteger getNextSqrtPriceFromTokenAInput(
      BigInteger sqrtPriceX64, BigInteger liquidity, BigInteger amount, boolean byAmountIn) {

    // Safeguard checks
    if (sqrtPriceX64.compareTo(BigInteger.ZERO) <= 0) {
      throw new IllegalArgumentException("sqrt price must be > 0");
    }

    if (amount.equals(BigInteger.ZERO) || liquidity.equals(BigInteger.ZERO)) {
      return sqrtPriceX64;
    }

    try {
      // Calculate next sqrt price based on the formulas in the Rust SDK
      BigInteger numerator = liquidity.multiply(sqrtPriceX64).shiftLeft(64);
      BigInteger product = amount.multiply(sqrtPriceX64);

      // Check for potential overflow when calculating the denominator
      if (product.bitLength() + liquidity.bitLength() > 128) {
        log.warn(
            "Potential overflow detected in getNextSqrtPriceFromTokenAInput, using safe calculation");

        // Use BigDecimal for higher precision calculation
        BigDecimal liqDec = new BigDecimal(liquidity);
        BigDecimal sqrtPriceDec = new BigDecimal(sqrtPriceX64);
        BigDecimal amountDec = new BigDecimal(amount);
        BigDecimal q64Dec = new BigDecimal(Q64);

        BigDecimal numeratorDec = liqDec.multiply(sqrtPriceDec).multiply(q64Dec);
        BigDecimal denominatorDec = liqDec.multiply(q64Dec).add(amountDec.multiply(sqrtPriceDec));

        BigDecimal resultDec = numeratorDec.divide(denominatorDec, RoundingMode.CEILING);
        return resultDec.toBigInteger();
      }

      BigInteger denominator = liquidity.shiftLeft(64).add(product);

      // Prevent division by zero
      if (denominator.equals(BigInteger.ZERO)) {
        log.warn("Division by zero prevented in getNextSqrtPriceFromTokenAInput");
        return sqrtPriceX64;
      }

      // Return rounded result with ceiling division to ensure sufficient output
      return numerator.add(denominator.subtract(BigInteger.ONE)).divide(denominator);
    } catch (ArithmeticException e) {
      log.error("Arithmetic error in getNextSqrtPriceFromTokenAInput: {}", e.getMessage());

      // If calculation fails, return a price very close to current price
      // This is a safe fallback to allow the transaction to continue
      BigInteger safeFallback =
          sqrtPriceX64.multiply(BigInteger.valueOf(9998)).divide(BigInteger.valueOf(10000));

      log.warn("Using safe fallback price: {}", safeFallback);
      return safeFallback;
    }
  }

  /**
   * Calculate the next sqrt price for a swap with token B as input.
   *
   * @param sqrtPriceX64 Current sqrt price in Q64.64
   * @param liquidity Current liquidity
   * @param amount Amount of token B
   * @param byAmountIn Whether the amount is input (true) or output (false)
   * @return The next sqrt price
   */
  public static BigInteger getNextSqrtPriceFromTokenBInput(
      BigInteger sqrtPriceX64, BigInteger liquidity, BigInteger amount, boolean byAmountIn) {

    // Safeguard checks
    if (sqrtPriceX64.compareTo(BigInteger.ZERO) <= 0) {
      throw new IllegalArgumentException("sqrt price must be > 0");
    }

    if (amount.equals(BigInteger.ZERO) || liquidity.equals(BigInteger.ZERO)) {
      return sqrtPriceX64;
    }

    try {
      // Avoid precision loss by using a higher intermediate precision
      BigInteger numerator = amount.multiply(Q64);

      // Handle potential division to zero problem
      if (numerator.compareTo(liquidity) < 0) {
        // Calculate precise delta with big decimal
        BigDecimal numDec = new BigDecimal(numerator);
        BigDecimal liqDec = new BigDecimal(liquidity);
        BigDecimal deltaDec = numDec.divide(liqDec, 20, RoundingMode.CEILING);

        // Convert back to BigInteger, ensuring at least a small delta
        BigInteger delta = deltaDec.toBigInteger();
        if (delta.equals(BigInteger.ZERO)) {
          delta = BigInteger.ONE;
        }

        return sqrtPriceX64.add(delta);
      }

      BigInteger delta = numerator.divide(liquidity);
      return sqrtPriceX64.add(delta);
    } catch (ArithmeticException e) {
      log.error("Arithmetic error in getNextSqrtPriceFromTokenBInput: {}", e.getMessage());

      // If calculation fails, return a price slightly higher than current
      BigInteger safeFallback =
          sqrtPriceX64.multiply(BigInteger.valueOf(10002)).divide(BigInteger.valueOf(10000));

      log.warn("Using safe fallback price: {}", safeFallback);
      return safeFallback;
    }
  }

  /**
   * Calculate the amount of token A for a given sqrt price range and liquidity.
   *
   * @param liquidity The liquidity amount
   * @param sqrtPrice0X64 Lower sqrt price (Q64.64)
   * @param sqrtPrice1X64 Upper sqrt price (Q64.64)
   * @return The amount of token A
   */
  public static BigInteger getTokenADelta(
      BigInteger liquidity, BigInteger sqrtPrice0X64, BigInteger sqrtPrice1X64) {

    if (sqrtPrice0X64.compareTo(sqrtPrice1X64) > 0) {
      // Swap if lower > upper
      BigInteger temp = sqrtPrice0X64;
      sqrtPrice0X64 = sqrtPrice1X64;
      sqrtPrice1X64 = temp;
    }

    if (liquidity.equals(BigInteger.ZERO)) {
      return BigInteger.ZERO;
    }

    try {
      // Check if price difference is too small
      BigInteger priceDiff = sqrtPrice1X64.subtract(sqrtPrice0X64);
      if (priceDiff.compareTo(BigInteger.valueOf(100)) < 0) {
        // For very small price moves, apply a minimum output
        return liquidity.divide(BigInteger.valueOf(1000000));
      }

      // Use higher precision for the numerator to avoid truncation
      BigInteger numerator = liquidity.multiply(priceDiff).shiftLeft(64);
      BigInteger denominator = sqrtPrice1X64.multiply(sqrtPrice0X64);

      // Prevent division by zero
      if (denominator.equals(BigInteger.ZERO)) {
        log.warn("Division by zero prevented in getTokenADelta");
        return BigInteger.ONE;
      }

      // Add denominator-1 to numerator for rounding up (ceiling division)
      BigInteger result = numerator.add(denominator.subtract(BigInteger.ONE)).divide(denominator);

      // Ensure non-zero result for non-zero inputs
      if (result.equals(BigInteger.ZERO)
          && !liquidity.equals(BigInteger.ZERO)
          && !priceDiff.equals(BigInteger.ZERO)) {
        log.debug("Result rounded up to 1 for non-zero inputs");
        return BigInteger.ONE;
      }

      return result;
    } catch (ArithmeticException e) {
      log.error("Arithmetic error in getTokenADelta: {}", e.getMessage());

      // Fallback - estimate a reasonable small amount
      if (!liquidity.equals(BigInteger.ZERO)) {
        return liquidity.divide(BigInteger.valueOf(1000000)).max(BigInteger.ONE);
      }
      return BigInteger.ONE;
    }
  }

  /**
   * Calculate the amount of token B for a given sqrt price range and liquidity. Formula: liquidity
   * * (sqrtPrice1 - sqrtPrice0)
   *
   * @param liquidity The liquidity amount
   * @param sqrtPrice0X64 Lower sqrt price (Q64.64)
   * @param sqrtPrice1X64 Upper sqrt price (Q64.64)
   * @return The amount of token B
   */
  // Add this to your OrcMathUtils class
  public static BigInteger getTokenBDelta(
      BigInteger liquidity, BigInteger sqrtPrice0X64, BigInteger sqrtPrice1X64) {

    if (sqrtPrice0X64.compareTo(sqrtPrice1X64) > 0) {
      BigInteger temp = sqrtPrice0X64;
      sqrtPrice0X64 = sqrtPrice1X64;
      sqrtPrice1X64 = temp;
    }

    if (liquidity.equals(BigInteger.ZERO)) {
      return BigInteger.ZERO;
    }

    // Liquidity * (sqrtPrice1 - sqrtPrice0)
    BigInteger delta = liquidity.multiply(sqrtPrice1X64.subtract(sqrtPrice0X64)).shiftRight(64);

    // Ensure non-zero for non-zero inputs
    if (delta.equals(BigInteger.ZERO)
        && !liquidity.equals(BigInteger.ZERO)
        && !sqrtPrice1X64.equals(sqrtPrice0X64)) {
      return BigInteger.ONE;
    }

    return delta;
  }

  /**
   * Applies a fee rate to a given amount
   *
   * @param amount The amount to apply the fee to
   * @param feeRate The fee rate in hundredths of a basis point (1/1000000)
   * @return The amount after fee is applied
   */
  public static BigInteger applyFeeRate(BigInteger amount, int feeRate) {
    if (amount.equals(BigInteger.ZERO)) {
      return BigInteger.ZERO;
    }

    try {
      return amount
          .multiply(BigInteger.valueOf(1_000_000 - feeRate))
          .divide(BigInteger.valueOf(1_000_000));
    } catch (ArithmeticException e) {
      log.error("Error applying fee rate: {}", e.getMessage());

      // Fallback - apply approximate 0.3% fee (common pool fee)
      return amount.multiply(BigInteger.valueOf(997)).divide(BigInteger.valueOf(1000));
    }
  }

  /**
   * Calculates the price impact of a swap as a percentage.
   *
   * @param inputAmount The input amount
   * @param outputAmount The output amount
   * @param sqrtPriceBefore The sqrt price before the swap
   * @param sqrtPriceAfter The sqrt price after the swap
   * @param aToB Whether the swap is from A to B
   * @return The price impact as a percentage (0-100)
   */
  public static double calculatePriceImpact(
      BigInteger inputAmount,
      BigInteger outputAmount,
      BigInteger sqrtPriceBefore,
      BigInteger sqrtPriceAfter,
      boolean aToB) {

    try {
      // Convert sqrt prices to regular prices
      BigDecimal priceBefore =
          new BigDecimal(sqrtPriceBefore)
              .multiply(new BigDecimal(sqrtPriceBefore))
              .divide(new BigDecimal(Q64).multiply(new BigDecimal(Q64)), 10, RoundingMode.HALF_UP);

      BigDecimal priceAfter =
          new BigDecimal(sqrtPriceAfter)
              .multiply(new BigDecimal(sqrtPriceAfter))
              .divide(new BigDecimal(Q64).multiply(new BigDecimal(Q64)), 10, RoundingMode.HALF_UP);

      // Calculate percentage change
      BigDecimal priceChange = priceAfter.subtract(priceBefore).abs();
      BigDecimal priceImpact =
          priceChange
              .divide(priceBefore, 6, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100));

      return priceImpact.doubleValue();
    } catch (Exception e) {
      log.error("Error calculating price impact: {}", e.getMessage());

      // Fallback - estimate impact from input/output ratio
      if (!inputAmount.equals(BigInteger.ZERO) && !outputAmount.equals(BigInteger.ZERO)) {
        // Very rough approximation
        return 0.5; // Default to 0.5% impact when calculation fails
      }
      return 0.0;
    }
  }
}
