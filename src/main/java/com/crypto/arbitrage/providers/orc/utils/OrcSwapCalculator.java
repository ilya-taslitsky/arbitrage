package com.crypto.arbitrage.providers.orc.utils;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.*;
import java.math.BigInteger;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculator for Orca Whirlpool swaps with proper tick traversal. This class is the heart of the
 * swap calculation logic.
 */
@Slf4j
public class OrcSwapCalculator {

  /** Result of a swap calculation with proper tick traversal. */
  @Data
  public static class SwapResult {
    private BigInteger amountIn; // Actual input amount consumed
    private BigInteger amountOut; // Output amount generated
    private BigInteger nextSqrtPrice; // New sqrt price after swap
    private int nextTickIndex; // New tick index after swap
    private int ticksCrossed; // Number of initialized ticks crossed
    private BigInteger feeAmount; // Fee amount paid
  }

  /** Result of a single swap step calculation. */
  @Data
  private static class SwapStepResult {
    private BigInteger amountIn; // Input consumed in this step
    private BigInteger amountOut; // Output generated in this step
    private BigInteger nextSqrtPrice; // New sqrt price after step
    private BigInteger feeAmount; // Fee amount for this step
  }

  /**
   * Calculates a swap with proper tick traversal. This is essential for accurate swap calculation
   * in concentrated liquidity pools.
   *
   * @param amountSpecified The amount of tokens to swap (in or out, depending on
   *     amountSpecifiedIsInput)
   * @param pool The Whirlpool to execute the swap on
   * @param tickArrays The tick arrays needed for the swap
   * @param aToB The swap direction (true = A to B, false = B to A)
   * @param amountSpecifiedIsInput Whether the amount specified is input (true) or output (false)
   * @return The result of the swap calculation
   */
  public static SwapResult calculateSwapWithTickTraversal(
      BigInteger amountSpecified,
      OrcWhirlpool pool,
      OrcTickArray[] tickArrays,
      boolean aToB,
      boolean amountSpecifiedIsInput) {

    log.debug(
        "Starting swap calculation: amount={}, aToB={}, amountSpecifiedIsInput={}",
        amountSpecified,
        aToB,
        amountSpecifiedIsInput);

    // Initialize swap calculation state
    BigInteger amountRemaining = amountSpecified;
    BigInteger amountCalculated = BigInteger.ZERO;
    BigInteger currentSqrtPrice = pool.getSqrtPrice();
    int currentTickIndex = pool.getTickCurrentIndex();
    BigInteger currentLiquidity = pool.getLiquidity();
    BigInteger totalFeeAmount = BigInteger.ZERO;
    int ticksCrossed = 0;

    log.debug(
        "Initial state: sqrtPrice={}, tickIndex={}, liquidity={}",
        currentSqrtPrice,
        currentTickIndex,
        currentLiquidity);

    // Continue swapping until amount is depleted or we can't proceed further
    while (amountRemaining.compareTo(BigInteger.ZERO) > 0
        && !currentLiquidity.equals(BigInteger.ZERO)) {
      // Find the next initialized tick in the direction of the swap
      int nextInitializedTickIndex =
          findNextInitializedTick(tickArrays, currentTickIndex, pool.getTickSpacing(), aToB);

      log.debug("Next initialized tick: {}", nextInitializedTickIndex);

      // Calculate the sqrt price for this tick
      BigInteger nextTickSqrtPrice = TickMath.tickIndexToSqrtPriceX64(nextInitializedTickIndex);

      // Apply bounds to the next price
      if (aToB && nextTickSqrtPrice.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
        nextTickSqrtPrice = OrcConstants.MIN_SQRT_PRICE;
      } else if (!aToB && nextTickSqrtPrice.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
        nextTickSqrtPrice = OrcConstants.MAX_SQRT_PRICE;
      }

      log.debug("Next tick sqrt price: {}", nextTickSqrtPrice);

      // Calculate swap step within the current price range
      SwapStepResult stepResult;
      if (amountSpecifiedIsInput) {
        stepResult =
            computeSwapStep(
                currentSqrtPrice,
                nextTickSqrtPrice,
                currentLiquidity,
                amountRemaining,
                pool.getFeeRate(),
                aToB);
      } else {
        stepResult =
            computeSwapStepGivenOut(
                currentSqrtPrice,
                nextTickSqrtPrice,
                currentLiquidity,
                amountRemaining,
                pool.getFeeRate(),
                aToB);
      }

      log.debug(
          "Step result: amountIn={}, amountOut={}, nextSqrtPrice={}",
          stepResult.getAmountIn(),
          stepResult.getAmountOut(),
          stepResult.getNextSqrtPrice());

      // Update state based on step results
      if (amountSpecifiedIsInput) {
        // For exact input, we consumed some of our input amount
        amountRemaining =
            amountRemaining.subtract(stepResult.getAmountIn().add(stepResult.getFeeAmount()));
        amountCalculated = amountCalculated.add(stepResult.getAmountOut());
      } else {
        // For exact output, we fulfilled some of our desired output amount
        amountRemaining = amountRemaining.subtract(stepResult.getAmountOut());
        amountCalculated =
            amountCalculated.add(stepResult.getAmountIn().add(stepResult.getFeeAmount()));
      }

      totalFeeAmount = totalFeeAmount.add(stepResult.getFeeAmount());
      currentSqrtPrice = stepResult.getNextSqrtPrice();

      // Check if we've crossed the target tick
      boolean crossedTick =
          aToB
              ? currentSqrtPrice.compareTo(nextTickSqrtPrice) <= 0
              : currentSqrtPrice.compareTo(nextTickSqrtPrice) >= 0;

      if (crossedTick) {
        // We crossed a tick boundary, we need to update liquidity
        ticksCrossed++;

        // Move to the next tick
        currentTickIndex = aToB ? nextInitializedTickIndex - 1 : nextInitializedTickIndex;

        // Update the liquidity if this was an initialized tick
        OrcTick tick = getTickFromTickArrays(tickArrays, nextInitializedTickIndex);
        if (tick != null && tick.isInitialized()) {
          BigInteger liquidityNet = tick.getLiquidityNet();

          if (aToB) {
            // When going down in price, subtract liquidityNet
            currentLiquidity = currentLiquidity.subtract(liquidityNet);
          } else {
            // When going up in price, add liquidityNet
            currentLiquidity = currentLiquidity.add(liquidityNet);
          }

          log.debug("Updated liquidity after crossing tick: {}", currentLiquidity);
        }
      } else {
        // We didn't cross the next tick but used all the input/output amount
        break;
      }
    }

    // Prepare the final result
    SwapResult result = new SwapResult();
    if (amountSpecifiedIsInput) {
      result.setAmountIn(amountSpecified.subtract(amountRemaining));
      result.setAmountOut(amountCalculated);
    } else {
      result.setAmountIn(amountCalculated);
      result.setAmountOut(amountSpecified.subtract(amountRemaining));
    }
    result.setNextSqrtPrice(currentSqrtPrice);
    result.setNextTickIndex(currentTickIndex);
    result.setTicksCrossed(ticksCrossed);
    result.setFeeAmount(totalFeeAmount);

    log.debug(
        "Final swap result: amountIn={}, amountOut={}, ticksCrossed={}, feeAmount={}",
        result.getAmountIn(),
        result.getAmountOut(),
        result.getTicksCrossed(),
        result.getFeeAmount());

    return result;
  }

  /**
   * Compute a single swap step within a price range. This calculates how much can be swapped within
   * the current price range.
   */
  private static SwapStepResult computeSwapStep(
      BigInteger sqrtPriceStart,
      BigInteger sqrtPriceTarget,
      BigInteger liquidity,
      BigInteger amountRemaining,
      int feeRate,
      boolean aToB) {

    // Apply fee rate to input amount
    BigInteger amountRemainingLessFee = OrcMathUtils.applyFeeRate(amountRemaining, feeRate);

    log.debug(
        "Computing swap step: amountRemaining={}, amountAfterFee={}",
        amountRemaining,
        amountRemainingLessFee);

    // Calculate next sqrt price based on inputs
    BigInteger nextSqrtPrice;
    BigInteger amountIn;
    BigInteger amountOut;

    if (aToB) {
      // A to B swap (price goes down)
      BigInteger sqrtPriceNextCandidate =
          aToB
              ? FixedPointMath.getNextSqrtPriceFromAmountAInPrecise(
                  sqrtPriceStart, liquidity, amountRemainingLessFee, true)
              : OrcMathUtils.getNextSqrtPriceFromTokenAInput(
                  sqrtPriceStart, liquidity, amountRemainingLessFee, true);

      // Check if we would cross the target price
      boolean crossesTarget = sqrtPriceNextCandidate.compareTo(sqrtPriceTarget) <= 0;
      nextSqrtPrice = crossesTarget ? sqrtPriceTarget : sqrtPriceNextCandidate;

      // Calculate amount in - this is how much of token A we consume
      amountIn =
          FixedPointMath.getPreciseTokenADelta(liquidity, nextSqrtPrice, sqrtPriceStart, false);

      // Calculate amount out - this is how much of token B we get
      amountOut =
          FixedPointMath.getPreciseTokenBDelta(liquidity, nextSqrtPrice, sqrtPriceStart, false);

    } else {
      // B to A swap (price goes up)
      BigInteger sqrtPriceNextCandidate =
          FixedPointMath.getNextSqrtPriceFromAmountBInPrecise(
              sqrtPriceStart, liquidity, amountRemainingLessFee, true);

      // Check if we would cross the target price
      boolean crossesTarget = sqrtPriceNextCandidate.compareTo(sqrtPriceTarget) >= 0;
      nextSqrtPrice = crossesTarget ? sqrtPriceTarget : sqrtPriceNextCandidate;

      // Calculate amount in - this is how much of token B we consume
      amountIn =
          FixedPointMath.getPreciseTokenBDelta(liquidity, sqrtPriceStart, nextSqrtPrice, false);

      // Calculate amount out - this is how much of token A we get
      amountOut =
          FixedPointMath.getPreciseTokenADelta(liquidity, sqrtPriceStart, nextSqrtPrice, false);
    }

    // Calculate fee amount
    BigInteger feeAmount = BigInteger.ZERO;
    if (feeRate > 0) {
      feeAmount =
          amountIn
              .multiply(BigInteger.valueOf(feeRate))
              .divide(BigInteger.valueOf(1_000_000 - feeRate));
    }

    // Ensure we have non-zero amounts to avoid program errors
    if (amountOut.equals(BigInteger.ZERO) && !liquidity.equals(BigInteger.ZERO)) {
      amountOut = BigInteger.ONE;
    }

    SwapStepResult result = new SwapStepResult();
    result.setAmountIn(amountIn);
    result.setAmountOut(amountOut);
    result.setNextSqrtPrice(nextSqrtPrice);
    result.setFeeAmount(feeAmount);

    return result;
  }

  /**
   * Compute a swap step for exact output swaps. This is more complex as we need to work backwards
   * from the desired output.
   */
  private static SwapStepResult computeSwapStepGivenOut(
      BigInteger sqrtPriceStart,
      BigInteger sqrtPriceTarget,
      BigInteger liquidity,
      BigInteger amountRemaining,
      int feeRate,
      boolean aToB) {

    // Calculate the target price that would give the desired output
    BigInteger nextSqrtPrice;
    BigInteger amountIn;
    BigInteger amountOut;

    if (aToB) {
      // For exact output A to B, we need a price that gives exactly amountRemaining of token B

      // Calculate how much we need to move the price to get amountRemaining of token B
      // For token B, the formula is ΔB = L * (sqrt(p1) - sqrt(p0))
      // To get ΔB = amountRemaining, we need sqrt(p1) = sqrt(p0) - amountRemaining/L
      BigInteger targetDelta = amountRemaining.shiftLeft(64).divide(liquidity);
      BigInteger sqrtPriceNextCandidate = sqrtPriceStart.subtract(targetDelta);

      // Check if this would cross the target price
      boolean crossesTarget = sqrtPriceNextCandidate.compareTo(sqrtPriceTarget) <= 0;
      nextSqrtPrice = crossesTarget ? sqrtPriceTarget : sqrtPriceNextCandidate;

      // Calculate the actual output for this price move
      amountOut =
          FixedPointMath.getPreciseTokenBDelta(liquidity, nextSqrtPrice, sqrtPriceStart, false);

      // Calculate the input needed
      amountIn =
          FixedPointMath.getPreciseTokenADelta(liquidity, nextSqrtPrice, sqrtPriceStart, true);

    } else {
      // For exact output B to A, calculate target sqrt price

      // For token A, we need to calculate the sqrt price that gives us amountRemaining token A
      // ΔA = L * (1/sqrt(p0) - 1/sqrt(p1))
      // This requires more complex math to solve for sqrt(p1)
      amountOut = amountRemaining;

      // Use the helper function that calculates amount B needed for exact A out
      amountIn =
          FixedPointMath.getAmountBForExactAOut(liquidity, sqrtPriceStart, amountRemaining, true);

      // Calculate the next sqrt price
      // Apply the formula sqrt(p1) = sqrt(p0) + amountIn/L
      BigInteger priceDelta = amountIn.multiply(OrcConstants.Q64).divide(liquidity);
      BigInteger sqrtPriceNextCandidate = sqrtPriceStart.add(priceDelta);

      // Check if this would cross the target price
      boolean crossesTarget = sqrtPriceNextCandidate.compareTo(sqrtPriceTarget) >= 0;
      nextSqrtPrice = crossesTarget ? sqrtPriceTarget : sqrtPriceNextCandidate;
    }

    // Calculate fee amount
    BigInteger feeAmount = BigInteger.ZERO;
    if (feeRate > 0) {
      feeAmount =
          amountIn
              .multiply(BigInteger.valueOf(feeRate))
              .divide(BigInteger.valueOf(1_000_000 - feeRate));
    }

    SwapStepResult result = new SwapStepResult();
    result.setAmountIn(amountIn);
    result.setAmountOut(amountOut);
    result.setNextSqrtPrice(nextSqrtPrice);
    result.setFeeAmount(feeAmount);

    return result;
  }

  /**
   * Find the next initialized tick in the direction of the swap. Traverses the tick arrays to find
   * the closest initialized tick.
   */
  private static int findNextInitializedTick(
      OrcTickArray[] tickArrays, int currentTickIndex, int tickSpacing, boolean aToB) {

    if (aToB) {
      // For A to B (price decreasing), find the next initialized tick below current
      int lowestSeenTick = Integer.MIN_VALUE;

      for (OrcTickArray tickArray : tickArrays) {
        if (tickArray == null || !tickArray.isInitialized()) continue;

        int startTickIndex = tickArray.getStartTickIndex();
        int endTickIndex = startTickIndex + (OrcConstants.TICK_ARRAY_SIZE - 1) * tickSpacing;

        // Skip if this array doesn't cover ticks below our current tick
        if (startTickIndex > currentTickIndex) continue;

        // This array could contain valid ticks - check each one
        OrcTick[] ticks = tickArray.getTicks();

        // Iterate through ticks in this array from highest to lowest
        for (int i = OrcConstants.TICK_ARRAY_SIZE - 1; i >= 0; i--) {
          int tickIndex = startTickIndex + i * tickSpacing;
          // Skip ticks at or above current
          if (tickIndex >= currentTickIndex) continue;

          OrcTick tick = ticks[i];
          if (tick != null && tick.isInitialized()) {
            // If this is higher than any we've seen so far, it's closer to current
            if (tickIndex > lowestSeenTick) {
              lowestSeenTick = tickIndex;
            }
          }
        }
      }

      // If we found an initialized tick, return it
      if (lowestSeenTick != Integer.MIN_VALUE) {
        return lowestSeenTick;
      }

      // If no initialized tick found, return the minimum tick
      return TickMath.MIN_TICK;

    } else {
      // For B to A (price increasing), find the next initialized tick above current
      int highestSeenTick = Integer.MAX_VALUE;

      for (OrcTickArray tickArray : tickArrays) {
        if (tickArray == null || !tickArray.isInitialized()) continue;

        int startTickIndex = tickArray.getStartTickIndex();
        int endTickIndex = startTickIndex + (OrcConstants.TICK_ARRAY_SIZE - 1) * tickSpacing;

        // Skip if this array doesn't cover ticks above our current tick
        if (endTickIndex < currentTickIndex) continue;

        // This array could contain valid ticks - check each one
        OrcTick[] ticks = tickArray.getTicks();

        // Iterate through ticks in this array from lowest to highest
        for (int i = 0; i < OrcConstants.TICK_ARRAY_SIZE; i++) {
          int tickIndex = startTickIndex + i * tickSpacing;
          // Skip ticks at or below current
          if (tickIndex <= currentTickIndex) continue;

          OrcTick tick = ticks[i];
          if (tick != null && tick.isInitialized()) {
            // If this is lower than any we've seen so far, it's closer to current
            if (tickIndex < highestSeenTick) {
              highestSeenTick = tickIndex;
            }
          }
        }
      }

      // If we found an initialized tick, return it
      if (highestSeenTick != Integer.MAX_VALUE) {
        return highestSeenTick;
      }

      // If no initialized tick found, return the maximum tick
      return TickMath.MAX_TICK;
    }
  }

  /** Gets a specific tick from the tick arrays by its index. */
  private static OrcTick getTickFromTickArrays(OrcTickArray[] tickArrays, int tickIndex) {
    for (OrcTickArray tickArray : tickArrays) {
      if (tickArray == null || !tickArray.isInitialized()) continue;

      int startTickIndex = tickArray.getStartTickIndex();
      int spacing = 1; // Default to 1 if tickSpacing is not available

      // Estimate tickSpacing from the index difference between first and second tick
      OrcTick[] ticks = tickArray.getTicks();
      if (ticks.length >= 2) {
        spacing =
            (ticks.length > 1)
                ? (startTickIndex + ticks.length - 1 - startTickIndex) / (ticks.length - 1)
                : 1;
      }

      // Check if this tick index falls within this array
      if (tickIndex >= startTickIndex
          && tickIndex <= startTickIndex + (OrcConstants.TICK_ARRAY_SIZE - 1) * spacing) {

        // Calculate the position in the array
        if (spacing == 0) spacing = 1; // Avoid divide by zero
        int arrayIndex = (tickIndex - startTickIndex) / spacing;

        if (arrayIndex >= 0 && arrayIndex < ticks.length) {
          return ticks[arrayIndex];
        }
      }
    }

    return null;
  }
}
