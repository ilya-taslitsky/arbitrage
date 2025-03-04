package com.crypto.arbitrage.providers.orc.utils;

import static com.crypto.arbitrage.providers.orc.constants.OrcConstants.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Utilities for fixed-point math operations required for Orca Whirlpool calculations. Implements
 * the same math operations as the Rust SDK.
 */
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

    // Calculate square root
    BigDecimal sqrtPrice = BigDecimal.valueOf(Math.sqrt(adjustedPrice.doubleValue()));

    // Convert to Q64.64 format
    BigDecimal q64Scale = new BigDecimal(Q64);
    BigDecimal scaledValue = sqrtPrice.multiply(q64Scale);

    // Boundary checks
    if (scaledValue.compareTo(new BigDecimal(MIN_SQRT_PRICE)) < 0) {
      return MIN_SQRT_PRICE;
    }
    if (scaledValue.compareTo(new BigDecimal(MAX_SQRT_PRICE)) > 0) {
      return MAX_SQRT_PRICE;
    }

    return scaledValue.toBigInteger();
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

    if (amount.equals(BigInteger.ZERO) || liquidity.equals(BigInteger.ZERO)) {
      return sqrtPriceX64;
    }

    // Calculate next sqrt price based on the formulas in the Rust SDK
    BigInteger numerator = liquidity.multiply(sqrtPriceX64).shiftLeft(64);
    BigInteger denominator = liquidity.shiftLeft(64).add(amount.multiply(sqrtPriceX64));

    // Return rounded result
    return numerator.add(denominator.subtract(BigInteger.ONE)).divide(denominator);
  }

  /**
   * Calculate the next sqrt price for a swap with token B as input. Formula: sqrtPriceX64 + (amount
   * * Q64 / liquidity)
   *
   * @param sqrtPriceX64 Current sqrt price in Q64.64
   * @param liquidity Current liquidity
   * @param amount Amount of token B
   * @param byAmountIn Whether the amount is input (true) or output (false)
   * @return The next sqrt price
   */
  public static BigInteger getNextSqrtPriceFromTokenBInput(
      BigInteger sqrtPriceX64, BigInteger liquidity, BigInteger amount, boolean byAmountIn) {

    if (amount.equals(BigInteger.ZERO) || liquidity.equals(BigInteger.ZERO)) {
      return sqrtPriceX64;
    }

    // Calculate the delta
    BigInteger delta = amount.multiply(Q64).divide(liquidity);

    // Return sqrt price + delta
    return sqrtPriceX64.add(delta);
  }

  /**
   * Calculate the amount of token A for a given sqrt price range and liquidity. Formula: liquidity
   * * (sqrtPrice1 - sqrtPrice0) / (sqrtPrice0 * sqrtPrice1)
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

    BigInteger numerator = liquidity.multiply(sqrtPrice1X64.subtract(sqrtPrice0X64)).shiftLeft(64);
    BigInteger denominator = sqrtPrice1X64.multiply(sqrtPrice0X64);
    return numerator.add(denominator.subtract(BigInteger.ONE)).divide(denominator);
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
  public static BigInteger getTokenBDelta(
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

    return liquidity.multiply(sqrtPrice1X64.subtract(sqrtPrice0X64)).shiftRight(64);
  }

  /**
   * Adjusts a sqrt price by applying slippage tolerance.
   *
   * @param sqrtPriceX64 The sqrt price to adjust
   * @param slippageBps Slippage tolerance in basis points (1/100 of 1%)
   * @param aToB Whether the swap is from token A to token B
   * @return The adjusted sqrt price with slippage applied
   */
  public static BigInteger getAdjustedSqrtPriceForSlippage(
      BigInteger sqrtPriceX64, int slippageBps, boolean aToB) {

    BigInteger slippageAdjustment =
        sqrtPriceX64.multiply(BigInteger.valueOf(slippageBps)).divide(BigInteger.valueOf(10000));

    if (aToB) {
      // For A→B swaps (price goes down), minimum acceptable price is lower
      return sqrtPriceX64.subtract(slippageAdjustment);
    } else {
      // For B→A swaps (price goes up), maximum acceptable price is higher
      return sqrtPriceX64.add(slippageAdjustment);
    }
  }

  /**
   * Applies a fee rate to a given amount
   *
   * @param amount The amount to apply the fee to
   * @param feeRate The fee rate in hundredths of a basis point (1/1000000)
   * @return The amount after fee is applied
   */
  public static BigInteger applyFeeRate(BigInteger amount, int feeRate) {
    return amount
        .multiply(BigInteger.valueOf(1_000_000 - feeRate))
        .divide(BigInteger.valueOf(1_000_000));
  }

  /**
   * Calculates the liquidity from a specific amount of token A.
   *
   * @param amount Amount of token A
   * @param sqrtPrice0X64 Lower sqrt price bound
   * @param sqrtPrice1X64 Upper sqrt price bound
   * @return The calculated liquidity
   */
  public static BigInteger getLiquidityFromTokenA(
      BigInteger amount, BigInteger sqrtPrice0X64, BigInteger sqrtPrice1X64) {

    if (sqrtPrice0X64.compareTo(sqrtPrice1X64) > 0) {
      BigInteger temp = sqrtPrice0X64;
      sqrtPrice0X64 = sqrtPrice1X64;
      sqrtPrice1X64 = temp;
    }

    if (amount.equals(BigInteger.ZERO)) {
      return BigInteger.ZERO;
    }

    BigInteger numerator = amount.multiply(sqrtPrice0X64).multiply(sqrtPrice1X64);
    BigInteger denominator = sqrtPrice1X64.subtract(sqrtPrice0X64).shiftLeft(64);

    return numerator.divide(denominator);
  }

  /**
   * Calculates the liquidity from a specific amount of token B.
   *
   * @param amount Amount of token B
   * @param sqrtPrice0X64 Lower sqrt price bound
   * @param sqrtPrice1X64 Upper sqrt price bound
   * @return The calculated liquidity
   */
  public static BigInteger getLiquidityFromTokenB(
      BigInteger amount, BigInteger sqrtPrice0X64, BigInteger sqrtPrice1X64) {

    if (sqrtPrice0X64.compareTo(sqrtPrice1X64) > 0) {
      BigInteger temp = sqrtPrice0X64;
      sqrtPrice0X64 = sqrtPrice1X64;
      sqrtPrice1X64 = temp;
    }

    if (amount.equals(BigInteger.ZERO)) {
      return BigInteger.ZERO;
    }

    return amount.shiftLeft(64).divide(sqrtPrice1X64.subtract(sqrtPrice0X64));
  }

  /**
   * Converts a tick index to its corresponding sqrt price in Q64.64 format. Implements the formula:
   * sqrtPrice = sqrt(1.0001^tickIndex) * 2^64
   *
   * @param tickIndex The tick index
   * @return The sqrt price in Q64.64 format
   */
  public static BigInteger tickIndexToSqrtPriceX64(int tickIndex) {
    if (tickIndex == 0) {
      return Q64;
    }

    // Precomputed values for sqrt(1.0001)^tickIndex based on powers of 2
    // This implementation uses a more efficient algorithm than direct calculation

    BigInteger price = Q64;

    if (tickIndex > 0) {
      // For positive tick indices

      if ((tickIndex & 0x1) != 0)
        price = price.multiply(BigInteger.valueOf(1000050000)).shiftRight(32);
      if ((tickIndex & 0x2) != 0)
        price = price.multiply(BigInteger.valueOf(1000100025)).shiftRight(32);
      if ((tickIndex & 0x4) != 0)
        price = price.multiply(BigInteger.valueOf(1000200101)).shiftRight(32);
      if ((tickIndex & 0x8) != 0)
        price = price.multiply(BigInteger.valueOf(1000400401)).shiftRight(32);
      if ((tickIndex & 0x10) != 0)
        price = price.multiply(BigInteger.valueOf(1000801603)).shiftRight(32);
      if ((tickIndex & 0x20) != 0)
        price = price.multiply(BigInteger.valueOf(1001605421)).shiftRight(32);
      if ((tickIndex & 0x40) != 0)
        price = price.multiply(BigInteger.valueOf(1003219099)).shiftRight(32);
      if ((tickIndex & 0x80) != 0)
        price = price.multiply(BigInteger.valueOf(1006452131)).shiftRight(32);
      if ((tickIndex & 0x100) != 0)
        price = price.multiply(BigInteger.valueOf(1012968496)).shiftRight(32);
      if ((tickIndex & 0x200) != 0)
        price = price.multiply(BigInteger.valueOf(1026258773)).shiftRight(32);
      if ((tickIndex & 0x400) != 0)
        price = price.multiply(BigInteger.valueOf(1053379078)).shiftRight(32);
      if ((tickIndex & 0x800) != 0)
        price = price.multiply(BigInteger.valueOf(1110376034)).shiftRight(32);
      if ((tickIndex & 0x1000) != 0)
        price = price.multiply(BigInteger.valueOf(1233210550)).shiftRight(32);
      if ((tickIndex & 0x2000) != 0)
        price = price.multiply(BigInteger.valueOf(1520423349)).shiftRight(32);
      if ((tickIndex & 0x4000) != 0)
        price = price.multiply(BigInteger.valueOf(2312582931L)).shiftRight(32);
      if ((tickIndex & 0x8000) != 0)
        price = price.multiply(BigInteger.valueOf(5349279240L)).shiftRight(32);
      if ((tickIndex & 0x10000) != 0)
        price = price.multiply(BigInteger.valueOf(28615034846L)).shiftRight(32);
      if ((tickIndex & 0x20000) != 0)
        price = price.multiply(new BigInteger("819574193128")).shiftRight(32);
      if ((tickIndex & 0x40000) != 0)
        price = price.multiply(new BigInteger("671709746128354")).shiftRight(32);

      return price;
    } else {
      // For negative tick indices
      int absTickIndex = Math.abs(tickIndex);

      if ((absTickIndex & 0x1) != 0)
        price = price.multiply(BigInteger.valueOf(999950000)).shiftRight(32);
      if ((absTickIndex & 0x2) != 0)
        price = price.multiply(BigInteger.valueOf(999900010)).shiftRight(32);
      if ((absTickIndex & 0x4) != 0)
        price = price.multiply(BigInteger.valueOf(999800040)).shiftRight(32);
      if ((absTickIndex & 0x8) != 0)
        price = price.multiply(BigInteger.valueOf(999600160)).shiftRight(32);
      if ((absTickIndex & 0x10) != 0)
        price = price.multiply(BigInteger.valueOf(999200639)).shiftRight(32);
      if ((absTickIndex & 0x20) != 0)
        price = price.multiply(BigInteger.valueOf(998402550)).shiftRight(32);
      if ((absTickIndex & 0x40) != 0)
        price = price.multiply(BigInteger.valueOf(996813691)).shiftRight(32);
      if ((absTickIndex & 0x80) != 0)
        price = price.multiply(BigInteger.valueOf(993642446)).shiftRight(32);
      if ((absTickIndex & 0x100) != 0)
        price = price.multiply(BigInteger.valueOf(987313835)).shiftRight(32);
      if ((absTickIndex & 0x200) != 0)
        price = price.multiply(BigInteger.valueOf(974791957)).shiftRight(32);
      if ((absTickIndex & 0x400) != 0)
        price = price.multiply(BigInteger.valueOf(950227535)).shiftRight(32);
      if ((absTickIndex & 0x800) != 0)
        price = price.multiply(BigInteger.valueOf(902932330)).shiftRight(32);
      if ((absTickIndex & 0x1000) != 0)
        price = price.multiply(BigInteger.valueOf(815784836)).shiftRight(32);
      if ((absTickIndex & 0x2000) != 0)
        price = price.multiply(BigInteger.valueOf(665416110)).shiftRight(32);
      if ((absTickIndex & 0x4000) != 0)
        price = price.multiply(BigInteger.valueOf(442651973)).shiftRight(32);
      if ((absTickIndex & 0x8000) != 0)
        price = price.multiply(BigInteger.valueOf(195919781)).shiftRight(32);
      if ((absTickIndex & 0x10000) != 0)
        price = price.multiply(BigInteger.valueOf(38367443)).shiftRight(32);
      if ((absTickIndex & 0x20000) != 0) price = price.multiply(BigInteger.valueOf(1472234));
      if ((absTickIndex & 0x40000) != 0) price = price.multiply(BigInteger.valueOf(2168));

      return price;
    }
  }

  /**
   * Converts a sqrt price in Q64.64 format to the corresponding tick index. Implements the formula:
   * tickIndex = log(sqrtPrice^2 / 2^128) / log(1.0001)
   *
   * @param sqrtPriceX64 The sqrt price in Q64.64 format
   * @return The closest tick index
   */
  public static int sqrtPriceToTickIndex(BigInteger sqrtPriceX64) {
    if (sqrtPriceX64.compareTo(BigInteger.ZERO) <= 0) {
      throw new IllegalArgumentException("Invalid sqrt price (must be > 0)");
    }

    // For exact matches to Q64, return 0
    if (sqrtPriceX64.equals(Q64)) {
      return 0;
    }

    // Calculate log base 1.0001 of (sqrtPrice^2 / 2^128)
    // log(sqrtPrice^2 / 2^128) / log(1.0001) = log(sqrtPrice^2) / log(1.0001) - log(2^128) /
    // log(1.0001)

    double sqrtPriceDouble = sqrtPriceX64.doubleValue() / Q64.doubleValue();
    double priceDouble = sqrtPriceDouble * sqrtPriceDouble;

    // log(price) / log(1.0001)
    double tickIndexDouble = Math.log(priceDouble) / Math.log(1.0001);

    // Round to the nearest integer
    int tickIndex = (int) Math.round(tickIndexDouble);

    return tickIndex;
  }

  // Add to your OrcMathUtils or OrcSwapService
  public double calculatePriceImpact(
      BigInteger inputAmount,
      BigInteger outputAmount,
      BigInteger sqrtPriceBefore,
      BigInteger sqrtPriceAfter,
      boolean aToB) {

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
        priceChange.divide(priceBefore, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

    return priceImpact.doubleValue();
  }
}
