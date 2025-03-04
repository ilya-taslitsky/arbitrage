package com.crypto.arbitrage.providers.orc.utils;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import lombok.extern.slf4j.Slf4j;

/**
 * Utilities for tick math operations required for Orca Whirlpool calculations. Implements the same
 * tick math as the Uniswap and Orca Whirlpool protocol.
 */
@Slf4j
public class TickMath {

  private static final MathContext MC = new MathContext(30, RoundingMode.HALF_UP);

  // Tick range constants
  public static final int MAX_TICK = 443636;
  public static final int MIN_TICK = -443636;

  /**
   * Converts a tick index to a sqrtPriceX64 value in Q64.64 format.
   *
   * @param tickIndex The tick index to convert
   * @return The sqrtPriceX64 value in Q64.64 format
   */
  public static BigInteger tickIndexToSqrtPriceX64(int tickIndex) {
    if (tickIndex < MIN_TICK || tickIndex > MAX_TICK) {
      throw new IllegalArgumentException("Tick index outside valid range: " + tickIndex);
    }

    boolean negative = tickIndex < 0;
    int abs = Math.abs(tickIndex);

    // Approximate sqrt(1.0001^tick) * 2^64

    // For very small tick values, use a simplified approach
    if (abs < 100) {
      double rawValue = Math.pow(1.0001, tickIndex);
      double sqrtValue = Math.sqrt(rawValue);
      return BigDecimal.valueOf(sqrtValue)
          .multiply(new BigDecimal(OrcConstants.Q64))
          .toBigInteger();
    }

    // For larger tick values, we use a more precise approach
    // 1.0001^(tick/2) = sqrt(1.0001^tick)
    BigDecimal value = BigDecimal.valueOf(1.0001).pow(tickIndex / 2, MC);

    // If tick is odd, multiply by sqrt(1.0001)
    if (tickIndex % 2 != 0) {
      value = value.multiply(BigDecimal.valueOf(Math.sqrt(1.0001)), MC);
    }

    // Convert to Q64.64 format
    return value.multiply(new BigDecimal(OrcConstants.Q64), MC).toBigInteger();
  }

  /**
   * Converts a sqrtPriceX64 to the corresponding tick index.
   *
   * @param sqrtPriceX64 The sqrt price in Q64.64 format
   * @return The tick index
   */
  public static int sqrtPriceX64ToTickIndex(BigInteger sqrtPriceX64) {
    if (sqrtPriceX64.compareTo(BigInteger.ZERO) <= 0) {
      throw new IllegalArgumentException("Sqrt price must be positive: " + sqrtPriceX64);
    }

    if (sqrtPriceX64.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
      return MIN_TICK;
    }

    if (sqrtPriceX64.compareTo(OrcConstants.MAX_SQRT_PRICE) >= 0) {
      return MAX_TICK;
    }

    // Convert sqrtPriceX64 to a decimal price
    BigDecimal sqrtPrice =
        new BigDecimal(sqrtPriceX64)
            .divide(new BigDecimal(OrcConstants.Q64), 20, RoundingMode.HALF_UP);

    // Square the price to get the actual price
    BigDecimal price = sqrtPrice.multiply(sqrtPrice, MC);

    // Calculate log base 1.0001 of the price
    // log_1.0001(price) = log(price) / log(1.0001)
    double logBase10Price = Math.log10(price.doubleValue());
    double logBase10_1_0001 = Math.log10(1.0001);
    double tickDouble = logBase10Price / logBase10_1_0001;

    // Round to the nearest tick
    int tick = (int) Math.round(tickDouble);

    // Ensure tick is in valid range
    return Math.max(MIN_TICK, Math.min(MAX_TICK, tick));
  }

  /**
   * Gets the price from a tick index. Price = 1.0001^tick
   *
   * @param tickIndex The tick index
   * @return The price as a double
   */
  public static double tickIndexToPrice(int tickIndex) {
    return Math.pow(1.0001, tickIndex);
  }

  /**
   * Gets the tick index from a price. tick = log_1.0001(price)
   *
   * @param price The price
   * @return The tick index
   */
  public static int priceToTickIndex(double price) {
    if (price <= 0) {
      throw new IllegalArgumentException("Price must be positive: " + price);
    }

    double logPrice = Math.log(price);
    double log1_0001 = Math.log(1.0001);
    int tick = (int) Math.round(logPrice / log1_0001);

    return Math.max(MIN_TICK, Math.min(MAX_TICK, tick));
  }

  /**
   * Gets the valid tick index below the given tick index based on the tick spacing.
   *
   * @param tickIndex The tick index
   * @param tickSpacing The tick spacing
   * @return The closest valid tick index below
   */
  public static int nearestValidTickIndex(int tickIndex, int tickSpacing) {
    int compressed = Math.floorDiv(tickIndex, tickSpacing);
    return compressed * tickSpacing;
  }
}
