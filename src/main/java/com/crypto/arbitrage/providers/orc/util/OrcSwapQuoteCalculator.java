package com.crypto.arbitrage.providers.orc.util;

import com.crypto.arbitrage.providers.orc.model.transaction.OrcSwapQuote;
import com.crypto.arbitrage.providers.orc.model.transaction.OrcTickArray;
import com.crypto.arbitrage.providers.orc.model.transaction.OrcWhirlpool;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrcSwapQuoteCalculator {

  /**
   * Calculates the swap quote for an exact–input swap.
   *
   * <p>This implementation is adapted from the official Rust SDK.
   *
   * @param inputAmount The input token amount.
   * @param specifiedTokenA True if token A is the input token.
   * @param slippageToleranceBps The slippage tolerance in basis points.
   * @param pool The Whirlpool pool state.
   * @param tickArrays The tick arrays fetched for the pool.
   * @param transferFeeA (Optional) Transfer fee for token A.
   * @param transferFeeB (Optional) Transfer fee for token B.
   * @return A real OrcSwapQuote.
   */
  public OrcSwapQuote calculateQuoteByInput(
      BigInteger inputAmount,
      boolean specifiedTokenA,
      int slippageToleranceBps,
      OrcWhirlpool pool,
      OrcTickArray[] tickArrays,
      Object transferFeeA,
      Object transferFeeB)
      throws Exception {

    // Parse key on–chain values (assume they are stored as BigInteger)
    BigInteger currentSqrtPrice = pool.getSqrtPrice();
    BigInteger liquidity = pool.getLiquidity();
    // You will also need pool.feeRate, etc.
    int feeRate = pool.getFeeRate(); // assuming feeRate is an int field in OrcWhirlpool

    // Determine target sqrt price from slippage (this depends on swap direction)
    // For example, for an exact–input swap from token A to token B, the price should not drop below
    // some minimum.
    // (In a real implementation, use the formula from the Rust SDK to compute targetSqrtPrice.)
    BigInteger targetSqrtPrice =
        OrcaSwapOperation.computeTargetSqrtPriceForInput(
            currentSqrtPrice, slippageToleranceBps, specifiedTokenA);

    // Compute swap result using the swap–operation logic (see OrcaSwapOperation below)
    OrcaSwapOperation.SwapResult swapResult =
        OrcaSwapOperation.computeSwap(
            inputAmount.longValue(),
            feeRate,
            liquidity,
            currentSqrtPrice,
            targetSqrtPrice,
            specifiedTokenA,
            true);

    // Reverse apply transfer fees (if needed) using your real transfer fee logic.
    // (Assume you have methods similar to try_apply_transfer_fee from the Rust SDK.)
    BigInteger tokenIn =
        inputAmount; // In an exact–input swap, tokenIn equals inputAmount (after fee adjustments)
    BigInteger estimatedAmountOut = BigInteger.valueOf(swapResult.tokenB);
    BigInteger tokenMinOut =
        estimatedAmountOut.subtract(
            estimatedAmountOut
                .multiply(BigInteger.valueOf(slippageToleranceBps))
                .divide(BigInteger.valueOf(10000)));

    OrcSwapQuote quote = new OrcSwapQuote();
    quote.setTokenIn(tokenIn);
    quote.setEstimatedAmountOut(estimatedAmountOut);
    quote.setTokenMinOut(tokenMinOut);
    quote.setLiquidityDelta(BigInteger.valueOf(swapResult.liquidityDelta));
    // For exact–input swap, estimatedAmountIn equals tokenIn, and tokenMaxIn is tokenIn plus a
    // buffer.
    quote.setEstimatedAmountIn(tokenIn);
    quote.setTokenMaxIn(tokenIn.add(BigInteger.valueOf(100)));
    return quote;
  }

  /**
   * Calculates the swap quote for an exact–output swap.
   *
   * <p>This method should be implemented similarly to calculateQuoteByInput, but using the reverse
   * swap simulation.
   */
  public OrcSwapQuote calculateQuoteByOutput(
      BigInteger outputAmount,
      boolean specifiedTokenA,
      int slippageToleranceBps,
      OrcWhirlpool pool,
      OrcTickArray[] tickArrays,
      Object transferFeeA,
      Object transferFeeB)
      throws Exception {
    // Implement the reverse calculation.
    // For brevity, we use a similar outline.
    BigInteger currentSqrtPrice = pool.getSqrtPrice();
    BigInteger liquidity = pool.getLiquidity();
    int feeRate = pool.getFeeRate();

    // For exact–output, compute target sqrt price based on output and slippage tolerance.
    BigInteger targetSqrtPrice =
        OrcaSwapOperation.computeTargetSqrtPriceForOutput(
            currentSqrtPrice, slippageToleranceBps, specifiedTokenA);

    OrcaSwapOperation.SwapResult swapResult =
        OrcaSwapOperation.computeSwap(
            outputAmount.longValue(),
            feeRate,
            liquidity,
            currentSqrtPrice,
            targetSqrtPrice,
            !specifiedTokenA,
            false);

    BigInteger estimatedAmountIn = BigInteger.valueOf(swapResult.tokenB);
    BigInteger tokenMaxIn =
        estimatedAmountIn.add(
            estimatedAmountIn
                .multiply(BigInteger.valueOf(slippageToleranceBps))
                .divide(BigInteger.valueOf(10000)));

    OrcSwapQuote quote = new OrcSwapQuote();
    quote.setTokenIn(estimatedAmountIn);
    quote.setEstimatedAmountIn(estimatedAmountIn);
    quote.setTokenMaxIn(tokenMaxIn);
    quote.setTokenMinOut(outputAmount);
    quote.setEstimatedAmountOut(outputAmount);
    quote.setLiquidityDelta(BigInteger.ZERO);
    return quote;
  }
}
