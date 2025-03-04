package com.crypto.arbitrage.providers.orc.utils;

package com.crypto.arbitrage.providers.orc.services;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.*;
import com.crypto.arbitrage.providers.orc.utils.OrcMathUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements proper tick traversal for calculating Orca Whirlpool swaps.
 * This is critical for accurate swap amount calculations in concentrated liquidity pools.
 */
@Slf4j
public class OrcSwapCalculator {

  /**
   * Result of a swap calculation with tick traversal.
   */
  @Data
  public static class SwapResult {
    private BigInteger amountIn;
    private BigInteger amountOut;
    private BigInteger nextSqrtPrice;
    private int nextTickIndex;
    private int ticksCrossed;
    private BigInteger feeAmount;
  }

  /**
   * Calculates a swap with proper tick traversal.
   * This method properly handles crossing price ticks during a swap, which is essential
   * for accurate calculations in concentrated liquidity pools.
   *
   * @param amountSpecified The amount of tokens to swap (can be input or output depending on amountSpecifiedIsInput)
   * @param pool The Whirlpool to execute the swap on
   * @param tickArrays The tick arrays needed for the swap
   * @param aToB The swap direction (true for A to B, false for B to A)
   * @param amountSpecifiedIsInput Whether the amount specified is input (true) or output (false)
   * @return The result of the swap calculation
   */
  public static SwapResult calculateSwapWithTickTraversal(
          BigInteger amountSpecified,
          OrcWhirlpool pool,
          OrcTickArray[] tickArrays,
          boolean aToB,
          boolean amountSpecifiedIsInput) {

    // Initialize swap calculation state
    BigInteger amountRemaining = amountSpecified;
    BigInteger amountCalculated = BigInteger.ZERO;
    BigInteger currentSqrtPrice = pool.getSqrtPrice();
    int currentTickIndex = pool.getTickCurrentIndex();
    BigInteger currentLiquidity = pool.getLiquidity();
    BigInteger totalFeeAmount = BigInteger.ZERO;
    int ticksCrossed = 0;

    // Continue swapping until amount is depleted or we can't proceed further
    while (amountRemaining.compareTo(BigInteger.ZERO) > 0 && !currentLiquidity.equals(BigInteger.ZERO)) {

      // Find the next initialized tick in the given direction
      int nextInitializedTickIndex = findNextInitializedTick(tickArrays, currentTickIndex, aToB);

      // Calculate the sqrt price for this tick
      BigInteger nextTickSqrtPrice = OrcMathUtils.tickIndexToSqrtPriceX64(nextInitializedTickIndex);

      // Adjust bounds based on direction
      if (aToB && nextTickSqrtPrice.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
        nextTickSqrtPrice = OrcConstants.MIN_SQRT_PRICE;
      } else if (!aToB && nextTickSqrtPrice.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
        nextTickSqrtPrice = OrcConstants.MAX_SQRT_PRICE;
      }

      // Calculate swap step within the current price range
      SwapStepResult stepResult;
      if (amountSpecifiedIsInput) {
        stepResult = computeSwapStep(
                currentSqrtPrice,
                nextTickSqrtPrice,
                currentLiquidity,
                amountRemaining,
                pool.getFeeRate(),
                aToB,
                true
        );
      } else {
        stepResult = computeSwapStepGivenOut(
                currentSqrtPrice,
                nextTickSqrtPrice,
                currentLiquidity,
                amountRemaining,
                pool.getFeeRate(),
                aToB
        );
      }

      // Update state
      if (amountSpecifiedIsInput) {
        amountRemaining = amountRemaining.subtract(stepResult.getAmountIn().add(stepResult.getFeeAmount()));
        amountCalculated = amountCalculated.add(stepResult.getAmountOut());
      } else {
        amountRemaining = amountRemaining.subtract(stepResult.getAmountOut());
        amountCalculated = amountCalculated.add(stepResult.getAmountIn().add(stepResult.getFeeAmount()));
      }

      totalFeeAmount = totalFeeAmount.add(stepResult.getFeeAmount());
      currentSqrtPrice = stepResult.getNextSqrtPrice();

      // Check if we've crossed a tick
      boolean crossedTick = aToB
              ? currentSqrtPrice.compareTo(nextTickSqrtPrice) <= 0
              : currentSqrtPrice.compareTo(nextTickSqrtPrice) >= 0;

      if (crossedTick) {
        ticksCrossed++;
        // Move to the next tick
        currentTickIndex = aToB ? nextInitializedTickIndex - 1 : nextInitializedTickIndex;

        // Update liquidity if crossing an initialized tick
        OrcTick tick = getTickFromTickArrays(tickArrays, nextInitializedTickIndex);
        if (tick != null && tick.isInitialized()) {
          BigInteger liquidityNet = tick.getLiquidityNet();
          if (aToB) {
            // When going down in price, we're exiting positions, so subtract liquidityNet
            currentLiquidity = currentLiquidity.subtract(liquidityNet);
          } else {
            // When going up in price, we're entering positions, so add liquidityNet
            currentLiquidity = currentLiquidity.add(liquidityNet);
          }
        }
      } else {
        // We didn't cross a tick but used up all the amount, so we're done
        break;
      }
    }

    // Prepare result
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

    return result;
  }

  /**
   * Compute a single swap step within a price range.
   */
  private static SwapStepResult computeSwapStep(
          BigInteger sqrtPriceStart,
          BigInteger sqrtPriceTarget,
          BigInteger liquidity,
          BigInteger amountRemaining,
          int feeRate,
          boolean aToB,
          boolean exactIn) {

    // Apply fee rate
    BigInteger amountRemainingLessFee = OrcMathUtils.applyFeeRate(amountRemaining, feeRate);

    // Calculate next sqrt price based on inputs
    BigInteger nextSqrtPrice;
    BigInteger amountIn;
    BigInteger amountOut;

    if (aToB) {
      // A to B swap (price goes down)
      BigInteger sqrtPriceNextCandidate = OrcMathUtils.getNextSqrtPriceFromTokenAInput(
              sqrtPriceStart, liquidity, amountRemainingLessFee, true);

      // Check if we would cross the target price
      boolean crossesTarget = sqrtPriceNextCandidate.compareTo(sqrtPriceTarget) <= 0;
      nextSqrtPrice = crossesTarget ? sqrtPriceTarget : sqrtPriceNextCandidate;

      // Calculate amount in - this is how much of input token we need
      amountIn = OrcMathUtils.getTokenADelta(
              liquidity, nextSqrtPrice, sqrtPriceStart);

      // Calculate amount out - this is how much of output token we get
      amountOut = OrcMathUtils.getTokenBDelta(
              liquidity, nextSqrtPrice, sqrtPriceStart);

    } else {
      // B to A swap (price goes up)
      BigInteger sqrtPriceNextCandidate = OrcMathUtils.getNextSqrtPriceFromTokenBInput(
              sqrtPriceStart, liquidity, amountRemainingLessFee, true);

      // Check if we would cross the target price
      boolean crossesTarget = sqrtPriceNextCandidate.compareTo(sqrtPriceTarget) >= 0;
      nextSqrtPrice = crossesTarget ? sqrtPriceTarget : sqrtPriceNextCandidate;

      // Calculate amount in - this is how much of input token we need
      amountIn = OrcMathUtils.getTokenBDelta(
              liquidity, sqrtPriceStart, nextSqrtPrice);

      // Calculate amount out - this is how much of output token we get
      amountOut = OrcMathUtils.getTokenADelta(
              liquidity, sqrtPriceStart, nextSqrtPrice);
    }

    // Calculate fee amount
    BigInteger feeAmount = BigInteger.ZERO;
    if (feeRate > 0) {
      feeAmount = amountIn.multiply(BigInteger.valueOf(feeRate))
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
   * Compute a swap step for exact output swaps.
   */
  private static SwapStepResult computeSwapStepGivenOut(
          BigInteger sqrtPriceStart,
          BigInteger sqrtPriceTarget,
          BigInteger liquidity,
          BigInteger amountRemaining,
          int feeRate,
          boolean aToB) {

    // Calculate the hypothetical next sqrt price if we were to swap exactly amountRemaining out
    BigInteger nextSqrtPrice;
    BigInteger amountIn;
    BigInteger amountOut;

    if (aToB) {
      // For exact output A to B, we need to calculate target sqrt price
      // that would give us exactly amountRemaining of token B
      BigInteger targetDelta = amountRemaining.shiftLeft(64).divide(liquidity);
      BigInteger sqrtPriceNextCandidate = sqrtPriceStart.subtract(targetDelta);

      // Check if we would cross the target price
      boolean crossesTarget = sqrtPriceNextCandidate.compareTo(sqrtPriceTarget) <= 0;
      nextSqrtPrice = crossesTarget ? sqrtPriceTarget : sqrtPriceNextCandidate;

      // Calculate the actual output for this price move
      amountOut = OrcMathUtils.getTokenBDelta(
              liquidity, nextSqrtPrice, sqrtPriceStart);

      // Calculate the input needed
      amountIn = OrcMathUtils.getTokenADelta(
              liquidity, nextSqrtPrice, sqrtPriceStart);

    } else {
      // For exact output B to A, calculate target sqrt price
      BigInteger targetDelta = amountRemaining.multiply(OrcConstants.Q64).divide(liquidity);
      BigInteger sqrtPriceNextCandidate = sqrtPriceStart.add(targetDelta);

      // Check if we would cross the target price
      boolean crossesTarget = sqrtPriceNextCandidate.compareTo(sqrtPriceTarget) >= 0;
      nextSqrtPrice = crossesTarget ? sqrtPriceTarget : sqrtPriceNextCandidate;

      // Calculate the actual output for this price move
      amountOut = OrcMathUtils.getTokenADelta(
              liquidity, sqrtPriceStart, nextSqrtPrice);

      // Calculate the input needed
      amountIn = OrcMathUtils.getTokenBDelta(
              liquidity, sqrtPriceStart, nextSqrtPrice);
    }

    // Calculate fee amount
    BigInteger feeAmount = BigInteger.ZERO;
    if (feeRate > 0) {
      feeAmount = amountIn.multiply(BigInteger.valueOf(feeRate))
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
   * Find the next initialized tick in the direction of the swap.
   */
  private static int findNextInitializedTick(OrcTickArray[] tickArrays, int currentTickIndex, boolean aToB) {
    // Find the next initialized tick in the direction of the swap
    if (aToB) {
      // For A to B (price decreasing), find the next initialized tick below current
      for (OrcTickArray tickArray : tickArrays) {
        if (tickArray == null || !tickArray.isInitialized()) continue;

        int startTickIndex = tickArray.getStartTickIndex();
        int endTickIndex = startTickIndex + (OrcConstants.TICK_ARRAY_SIZE - 1) * pool.getTickSpacing();

        // Skip if this array doesn't cover ticks below our current tick
        if (startTickIndex > currentTickIndex) continue;

        // Check each tick in the array, starting from the current tick and going down
        for (int i = 0; i < OrcConstants.TICK_ARRAY_SIZE; i++) {
          int tickIndex = startTickIndex + i * pool.getTickSpacing();
          if (tickIndex >= currentTickIndex) continue;

          OrcTick tick = tickArray.getTicks()[i];
          if (tick != null && tick.isInitialized()) {
            return tickIndex;
          }
        }
      }

      // If no initialized tick found, return the minimum tick
      return TickMath.MIN_TICK;

    } else {
      // For B to A (price increasing), find the next initialized tick above current
      for (OrcTickArray tickArray : tickArrays) {
        if (tickArray == null || !tickArray.isInitialized()) continue;

        int startTickIndex = tickArray.getStartTickIndex();
        int endTickIndex = startTickIndex + (OrcConstants.TICK_ARRAY_SIZE - 1) * pool.getTickSpacing();

        // Skip if this array doesn't cover ticks above our current tick
        if (endTickIndex < currentTickIndex) continue;

        // Check each tick in the array, starting from the current tick and going up
        for (int i = OrcConstants.TICK_ARRAY_SIZE - 1; i >= 0; i--) {
          int tickIndex = startTickIndex + i * pool.getTickSpacing();
          if (tickIndex <= currentTickIndex) continue;

          OrcTick tick = tickArray.getTicks()[i];
          if (tick != null && tick.isInitialized()) {
            return tickIndex;
          }
        }
      }

      // If no initialized tick found, return the maximum tick
      return TickMath.MAX_TICK;
    }
  }

  /**
   * Get a tick from the tick arrays by its index.
   */
  private static OrcTick getTickFromTickArrays(OrcTickArray[] tickArrays, int tickIndex) {
    for (OrcTickArray tickArray : tickArrays) {
      if (tickArray == null || !tickArray.isInitialized()) continue;

      int startTickIndex = tickArray.getStartTickIndex();
      int endTickIndex = startTickIndex + (OrcConstants.TICK_ARRAY_SIZE - 1) * pool.getTickSpacing();

      if (tickIndex >= startTickIndex && tickIndex <= endTickIndex) {
        // Calculate the position in the array
        int spacing = pool.getTickSpacing();
        if (spacing == 0) spacing = 1; // Safeguard against divide by zero

        int arrayIndex = (tickIndex - startTickIndex) / spacing;
        if (arrayIndex >= 0 && arrayIndex < OrcConstants.TICK_ARRAY_SIZE) {
          return tickArray.getTicks()[arrayIndex];
        }
      }
    }

    return null;
  }

  /**
   * Internal class to represent the result of a single swap step.
   */
  @Data
  private static class SwapStepResult {
    private BigInteger amountIn;
    private BigInteger amountOut;
    private BigInteger nextSqrtPrice;
    private BigInteger feeAmount;
  }
}
