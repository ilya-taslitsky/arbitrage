package com.crypto.arbitrage.providers.orc.utils;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import java.math.BigInteger;

/** Simplified fixed-point math for Orca Whirlpool calculations. */
public class FixedPointMathSimple {

  /** Calculate token B amount for a given price change. */
  public static BigInteger getTokenBAmount(
      BigInteger liquidity, BigInteger sqrtPriceStart, BigInteger sqrtPriceTarget) {

    if (sqrtPriceStart.compareTo(sqrtPriceTarget) > 0) {
      BigInteger temp = sqrtPriceStart;
      sqrtPriceStart = sqrtPriceTarget;
      sqrtPriceTarget = temp;
    }

    // Simplified calculation: Liquidity * (sqrtPriceTarget - sqrtPriceStart) / 2^64
    return liquidity.multiply(sqrtPriceTarget.subtract(sqrtPriceStart)).shiftRight(64);
  }

  /** Calculate token A amount for a given price change. */
  public static BigInteger getTokenAAmount(
      BigInteger liquidity, BigInteger sqrtPriceStart, BigInteger sqrtPriceTarget) {

    if (sqrtPriceStart.compareTo(sqrtPriceTarget) > 0) {
      BigInteger temp = sqrtPriceStart;
      sqrtPriceStart = sqrtPriceTarget;
      sqrtPriceTarget = temp;
    }

    // L * (P1 - P0) * 2^64 / (P1 * P0)
    BigInteger numerator =
        liquidity.multiply(sqrtPriceTarget.subtract(sqrtPriceStart)).shiftLeft(64);

    BigInteger denominator = sqrtPriceTarget.multiply(sqrtPriceStart);

    if (denominator.equals(BigInteger.ZERO)) {
      return BigInteger.ONE;
    }

    return numerator.divide(denominator);
  }

  /** Calculate next sqrt price for token A input. */
  public static BigInteger getNextSqrtPrice(
      BigInteger sqrtPriceX64, BigInteger liquidity, BigInteger amount, boolean aToB) {

    if (aToB) {
      // For A to B swap
      if (amount.equals(BigInteger.ZERO) || liquidity.equals(BigInteger.ZERO)) {
        return sqrtPriceX64;
      }

      // Quotient = (liquidity * sqrtPrice * 2^64) / (liquidity * 2^64 + amount * sqrtPrice)
      BigInteger numerator = liquidity.multiply(sqrtPriceX64).shiftLeft(64);
      BigInteger product = amount.multiply(sqrtPriceX64);
      BigInteger denominator = liquidity.shiftLeft(64).add(product);

      if (denominator.equals(BigInteger.ZERO)) {
        return sqrtPriceX64;
      }

      BigInteger newSqrtPrice = numerator.divide(denominator);

      // Apply bounds
      if (newSqrtPrice.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
        return OrcConstants.MIN_SQRT_PRICE;
      }

      return newSqrtPrice;
    } else {
      // For B to A swap
      if (amount.equals(BigInteger.ZERO) || liquidity.equals(BigInteger.ZERO)) {
        return sqrtPriceX64;
      }

      // Quotient = sqrtPrice + amount * 2^64 / liquidity
      BigInteger quotient = amount.shiftLeft(64).divide(liquidity);
      BigInteger newSqrtPrice = sqrtPriceX64.add(quotient);

      // Apply bounds
      if (newSqrtPrice.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
        return OrcConstants.MAX_SQRT_PRICE;
      }

      return newSqrtPrice;
    }
  }
}
