package com.crypto.arbitrage.providers.orc.utils;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import lombok.extern.slf4j.Slf4j;

/**
 * Additional math utilities for fixed-point calculations in Orca Whirlpools. These utilities
 * provide more precise implementations of the math operations.
 */
@Slf4j
public class FixedPointMath {

  private static final MathContext MC = new MathContext(30, RoundingMode.HALF_UP);

  /**
   * Multiply two Q64.64 numbers and get a Q64.64 result.
   *
   * @param a First Q64.64 number
   * @param b Second Q64.64 number
   * @return Result in Q64.64 format
   */
  public static BigInteger mulQ64(BigInteger a, BigInteger b) {
    BigInteger result = a.multiply(b).shiftRight(64);
    return result;
  }

  /**
   * Divide two Q64.64 numbers and get a Q64.64 result.
   *
   * @param a Numerator in Q64.64 format
   * @param b Denominator in Q64.64 format
   * @return Result in Q64.64 format
   */
  public static BigInteger divQ64(BigInteger a, BigInteger b) {
    if (b.equals(BigInteger.ZERO)) {
      throw new ArithmeticException("Division by zero");
    }

    // Convert to higher precision to avoid loss
    BigInteger aScaled = a.shiftLeft(64);
    BigInteger result = aScaled.divide(b);
    return result;
  }

  /**
   * Converts a token amount to a precise BigDecimal with proper decimal places.
   *
   * @param amount The token amount as raw integer
   * @param decimals The number of decimals the token has
   * @return The token amount as a BigDecimal with proper decimal places
   */
  public static BigDecimal tokenAmountToDecimal(BigInteger amount, int decimals) {
    return new BigDecimal(amount)
        .divide(BigDecimal.TEN.pow(decimals), decimals, RoundingMode.HALF_UP);
  }

  /**
   * Converts a BigDecimal token amount to raw integer representation.
   *
   * @param amount The token amount as BigDecimal
   * @param decimals The number of decimals the token has
   * @return The token amount as raw integer
   */
  public static BigInteger decimalToTokenAmount(BigDecimal amount, int decimals) {
    return amount.multiply(BigDecimal.TEN.pow(decimals)).toBigInteger();
  }

  /**
   * Calculate a more precise token A delta for a price range movement. Handles boundary cases
   * better than the original implementation.
   */
  public static BigInteger getPreciseTokenADelta(
      BigInteger liquidity, BigInteger sqrtPrice0X64, BigInteger sqrtPrice1X64, boolean roundUp) {

    if (sqrtPrice0X64.compareTo(sqrtPrice1X64) > 0) {
      BigInteger temp = sqrtPrice0X64;
      sqrtPrice0X64 = sqrtPrice1X64;
      sqrtPrice1X64 = temp;
    }

    if (liquidity.equals(BigInteger.ZERO)) {
      return BigInteger.ZERO;
    }

    try {
      // Liquidity * (sqrtPrice1 - sqrtPrice0) * 2^64 / (sqrtPrice1 * sqrtPrice0)
      BigInteger dividend = liquidity.multiply(sqrtPrice1X64.subtract(sqrtPrice0X64)).shiftLeft(64);

      BigInteger divisor = sqrtPrice1X64.multiply(sqrtPrice0X64);

      if (divisor.equals(BigInteger.ZERO)) {
        log.warn("Division by zero prevented in getPreciseTokenADelta");
        return BigInteger.ONE;
      }

      BigInteger quotient;
      if (roundUp) {
        // Add (divisor - 1) to dividend for ceiling division
        quotient = dividend.add(divisor.subtract(BigInteger.ONE)).divide(divisor);
      } else {
        quotient = dividend.divide(divisor);
      }

      return quotient;
    } catch (ArithmeticException e) {
      log.error("Arithmetic error in getPreciseTokenADelta: {}", e.getMessage());
      return BigInteger.ONE; // Return minimal amount on error
    }
  }

  /**
   * Calculate a more precise token B delta for a price range movement. Handles boundary cases
   * better than the original implementation.
   */
  public static BigInteger getPreciseTokenBDelta(
      BigInteger liquidity, BigInteger sqrtPrice0X64, BigInteger sqrtPrice1X64, boolean roundUp) {

    if (sqrtPrice0X64.compareTo(sqrtPrice1X64) > 0) {
      BigInteger temp = sqrtPrice0X64;
      sqrtPrice0X64 = sqrtPrice1X64;
      sqrtPrice1X64 = temp;
    }

    if (liquidity.equals(BigInteger.ZERO)) {
      return BigInteger.ZERO;
    }

    try {
      // Liquidity * (sqrtPrice1 - sqrtPrice0)
      BigInteger delta = liquidity.multiply(sqrtPrice1X64.subtract(sqrtPrice0X64)).shiftRight(64);

      if (delta.equals(BigInteger.ZERO) && !liquidity.equals(BigInteger.ZERO)) {
        // If we rounded down to zero but had non-zero inputs, return at least 1
        return BigInteger.ONE;
      }

      return delta;
    } catch (ArithmeticException e) {
      log.error("Arithmetic error in getPreciseTokenBDelta: {}", e.getMessage());
      return BigInteger.ONE; // Return minimal amount on error
    }
  }

  /** Get the next sqrt price from amount A in, with improved precision. */
  public static BigInteger getNextSqrtPriceFromAmountAInPrecise(
      BigInteger sqrtPriceX64, BigInteger liquidity, BigInteger amount, boolean byAmountIn) {

    if (amount.equals(BigInteger.ZERO) || liquidity.equals(BigInteger.ZERO)) {
      return sqrtPriceX64;
    }

    // Liquidity * sqrtPrice
    BigInteger product = mulQ64(liquidity, sqrtPriceX64);

    // amount * sqrtPrice
    BigInteger amountProduct = mulQ64(amount, sqrtPriceX64);

    // liquidity * Q64 + (amount * sqrtPrice)
    BigInteger denominator = liquidity.shiftLeft(64).add(amountProduct);

    // (liquidity * sqrtPrice * Q64) / denominator
    BigInteger result = divQ64(product.shiftLeft(64), denominator);

    // Never return a price less than minimum
    if (result.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
      return OrcConstants.MIN_SQRT_PRICE;
    }

    return result;
  }

  /** Get the next sqrt price from amount B in, with improved precision. */
  public static BigInteger getNextSqrtPriceFromAmountBInPrecise(
      BigInteger sqrtPriceX64, BigInteger liquidity, BigInteger amount, boolean byAmountIn) {

    if (amount.equals(BigInteger.ZERO) || liquidity.equals(BigInteger.ZERO)) {
      return sqrtPriceX64;
    }

    // amount * Q64 / liquidity + sqrtPrice
    BigInteger quotient = divQ64(amount.shiftLeft(64), liquidity);
    BigInteger result = sqrtPriceX64.add(quotient);

    // Never return a price more than maximum
    if (result.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
      return OrcConstants.MAX_SQRT_PRICE;
    }

    return result;
  }

  /** Calculate the amount of token A needed for a given output amount of token B. */
  public static BigInteger getAmountAForExactBOut(
      BigInteger liquidity, BigInteger sqrtPriceX64, BigInteger amountOut, boolean roundUp) {

    if (amountOut.equals(BigInteger.ZERO) || liquidity.equals(BigInteger.ZERO)) {
      return BigInteger.ZERO;
    }

    // Calculate the price movement needed to get exactly amountOut of token B
    // amountOut << 64 / liquidity
    BigInteger priceDelta = divQ64(amountOut.shiftLeft(64), liquidity);

    // Ensure price doesn't go below minimum
    BigInteger nextSqrtPrice = sqrtPriceX64.subtract(priceDelta);
    if (nextSqrtPrice.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
      nextSqrtPrice = OrcConstants.MIN_SQRT_PRICE;
    }

    // Calculate the amount of token A needed for this price movement
    return getPreciseTokenADelta(liquidity, nextSqrtPrice, sqrtPriceX64, roundUp);
  }

  /** Calculate the amount of token B needed for a given output amount of token A. */
  public static BigInteger getAmountBForExactAOut(
      BigInteger liquidity, BigInteger sqrtPriceX64, BigInteger amountOut, boolean roundUp) {

    if (amountOut.equals(BigInteger.ZERO) || liquidity.equals(BigInteger.ZERO)) {
      return BigInteger.ZERO;
    }

    // To get amountOut of A, we need to increase the price from sqrtPrice to nextSqrtPrice
    // amountOut * sqrtPrice * sqrtPrice / (liquidity * 2^64)
    BigInteger product = mulQ64(amountOut, mulQ64(sqrtPriceX64, sqrtPriceX64));
    BigInteger priceDelta = divQ64(product, liquidity);

    // Ensure price doesn't go above maximum
    BigInteger nextSqrtPrice = sqrtPriceX64.add(priceDelta);
    if (nextSqrtPrice.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
      nextSqrtPrice = OrcConstants.MAX_SQRT_PRICE;
    }

    // Calculate the amount of token B needed for this price movement
    return getPreciseTokenBDelta(liquidity, sqrtPriceX64, nextSqrtPrice, roundUp);
  }
}
