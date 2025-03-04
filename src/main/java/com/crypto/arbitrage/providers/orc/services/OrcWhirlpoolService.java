package com.crypto.arbitrage.providers.orc.services;

import static com.crypto.arbitrage.providers.orc.constants.OrcConstants.*;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.*;
import com.crypto.arbitrage.providers.orc.utils.OrcAddressUtils;
import com.crypto.arbitrage.providers.orc.utils.OrcMathUtils;
import java.math.BigInteger;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.sava.core.accounts.Signer;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.Base58;
import software.sava.core.tx.Instruction;

/**
 * Main service for Orca Whirlpool operations. This service provides high-level methods for working
 * with Orca Whirlpools.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrcWhirlpoolService {

  // Core services
  private final OrcAccountFetcher accountFetcher;
  private final OrcSwapService swapService;
  private final OrcTokenAccountService tokenAccountService;
  private final OrcTransactionService transactionService;
  private final OrcWhirlpoolManagementService whirlpoolManagementService;

  /**
   * Swaps tokens in an Orca Whirlpool.
   *
   * @param amount The amount to swap
   * @param inputMint The mint of the input token
   * @param poolAddress The address of the pool to use
   * @param swapType The type of swap (exact input or exact output)
   * @param slippageToleranceBps The slippage tolerance in basis points
   * @return The result of the swap transaction
   * @throws Exception If there's an error during the swap
   */
  public OrcSwapTransactionResult swapTokens(
      BigInteger amount,
      String inputMint,
      String poolAddress,
      OrcSwapType swapType,
      Integer slippageToleranceBps)
      throws Exception {

    // Use default values if not provided
    String usePoolAddress = poolAddress != null ? poolAddress : SOL_USDC_POOL;
    int useSlippage =
        slippageToleranceBps != null
            ? slippageToleranceBps
            : OrcConstants.DEFAULT_SLIPPAGE_TOLERANCE_BPS;

    log.info("Swapping {} tokens on pool {}", amount, usePoolAddress);

    // Create signer from private key
    Signer signer = Signer.createFromPrivateKey(Base58.decode(MY_WALLET_PRIVATE_KEY));

    // Execute the swap
    return swapService.executeSwap(
        usePoolAddress, amount, inputMint, swapType, useSlippage, MY_WALLET_ADDRESS, signer);
  }

  /**
   * Swaps SOL to USDC.
   *
   * @param solAmount The amount of SOL to swap (in lamports)
   * @param slippageToleranceBps The slippage tolerance in basis points
   * @return The result of the swap transaction
   * @throws Exception If there's an error during the swap
   */
  public OrcSwapTransactionResult swapSolToUsdc(BigInteger solAmount, Integer slippageToleranceBps)
      throws Exception {
    return swapTokens(
        solAmount,
        OrcConstants.WSOL_MINT,
        SOL_USDC_POOL,
        OrcSwapType.EXACT_IN,
        slippageToleranceBps);
  }

  /**
   * Swaps USDC to SOL.
   *
   * @param usdcAmount The amount of USDC to swap (in smallest units)
   * @param slippageToleranceBps The slippage tolerance in basis points
   * @return The result of the swap transaction
   * @throws Exception If there's an error during the swap
   */
  public OrcSwapTransactionResult swapUsdcToSol(BigInteger usdcAmount, Integer slippageToleranceBps)
      throws Exception {
    return swapTokens(
        usdcAmount,
        OrcConstants.USDC_MINT,
        SOL_USDC_POOL,
        OrcSwapType.EXACT_IN,
        slippageToleranceBps);
  }

  /**
   * Gets the current price of a Whirlpool.
   *
   * @param poolAddress The address of the Whirlpool
   * @return The current price
   * @throws Exception If there's an error fetching the pool
   */
  public double getPoolPrice(String poolAddress) throws Exception {
    OrcWhirlpool pool = accountFetcher.fetchWhirlpool(poolAddress);

    // Get token decimals (using standard values for common tokens)
    int decimalsA = getTokenDecimals(pool.getTokenMintA());
    int decimalsB = getTokenDecimals(pool.getTokenMintB());

    // Convert sqrt price to price
    return OrcMathUtils.sqrtPriceX64ToPrice(pool.getSqrtPrice(), decimalsA, decimalsB);
  }

  /**
   * Finds arbitrage opportunities between pools.
   *
   * @param poolAddresses List of pool addresses to check
   * @param tokenMint The mint of the token to use as input
   * @param minAmount Minimum amount to consider
   * @param maxAmount Maximum amount to consider
   * @param minProfitBps Minimum profit in basis points
   * @return The best arbitrage opportunity if found, otherwise null
   */
  public OrcArbitrageOpportunity findArbitrageOpportunity(
      List<String> poolAddresses,
      String tokenMint,
      BigInteger minAmount,
      BigInteger maxAmount,
      int minProfitBps) {

    log.info("Looking for arbitrage opportunities across {} pools", poolAddresses.size());

    List<OrcArbitrageOpportunity> opportunities = new ArrayList<>();

    // Generate all possible pairs of pools
    for (int i = 0; i < poolAddresses.size(); i++) {
      for (int j = i + 1; j < poolAddresses.size(); j++) {
        // Try both directions for each pair
        OrcArbitrageOpportunity opp1 =
            checkArbitrageForPoolPair(
                poolAddresses.get(i), poolAddresses.get(j), minAmount, tokenMint);

        if (opp1 != null && opp1.getProfitBasisPoints() >= minProfitBps) {
          opportunities.add(opp1);
        }

        OrcArbitrageOpportunity opp2 =
            checkArbitrageForPoolPair(
                poolAddresses.get(j), poolAddresses.get(i), minAmount, tokenMint);

        if (opp2 != null && opp2.getProfitBasisPoints() >= minProfitBps) {
          opportunities.add(opp2);
        }
      }
    }

    // Find the most profitable opportunity
    if (!opportunities.isEmpty()) {
      opportunities.sort(
          (a, b) -> Integer.compare(b.getProfitBasisPoints(), a.getProfitBasisPoints()));

      OrcArbitrageOpportunity bestOpportunity = opportunities.get(0);

      // If very profitable, try to optimize the amount
      if (bestOpportunity.getProfitBasisPoints() >= minProfitBps * 2) {
        bestOpportunity = optimizeArbitrageAmount(bestOpportunity, minAmount, maxAmount);
      }

      return bestOpportunity;
    }

    return null;
  }

  /**
   * Executes an arbitrage opportunity.
   *
   * @param opportunity The arbitrage opportunity to execute
   * @return The result of the arbitrage operation
   */
  public OrcArbitrageResult executeArbitrage(OrcArbitrageOpportunity opportunity) {
    try {
      log.info(
          "Executing arbitrage between pools {} and {}",
          opportunity.getFirstPoolAddress(),
          opportunity.getSecondPoolAddress());

      // Create signer from private key
      Signer signer = Signer.createFromPrivateKey(Base58.decode(MY_WALLET_PRIVATE_KEY));

      // Execute first swap
      OrcSwapTransactionResult firstSwap =
          swapService.executeSwap(
              opportunity.getFirstPoolAddress(),
              opportunity.getInputAmount(),
              opportunity.getInputToken(),
              OrcSwapType.EXACT_IN,
              50, // 0.5% slippage
              MY_WALLET_ADDRESS,
              signer);

      if (!firstSwap.isSuccess()) {
        return OrcArbitrageResult.builder()
            .success(false)
            .resultMessage("First swap failed: " + firstSwap.getErrorMessage())
            .build();
      }

      // Check that we got at least 98% of expected output
      BigInteger actualOutput = BigInteger.valueOf(firstSwap.getTokenEstOut());
      BigInteger minExpectedOutput =
          opportunity
              .getIntermediateAmount()
              .multiply(BigInteger.valueOf(9800))
              .divide(BigInteger.valueOf(10000));

      if (actualOutput.compareTo(minExpectedOutput) < 0) {
        log.warn(
            "First swap output below expected: {} < {}",
            actualOutput,
            opportunity.getIntermediateAmount());

        return OrcArbitrageResult.builder()
            .success(false)
            .firstTransactionSignature(firstSwap.getTransactionSignature())
            .resultMessage("First swap output below expected")
            .build();
      }

      // Execute second swap
      OrcSwapTransactionResult secondSwap =
          swapService.executeSwap(
              opportunity.getSecondPoolAddress(),
              actualOutput,
              opportunity.getIntermediateToken(),
              OrcSwapType.EXACT_IN,
              50, // 0.5% slippage
              MY_WALLET_ADDRESS,
              signer);

      if (!secondSwap.isSuccess()) {
        return OrcArbitrageResult.builder()
            .success(false)
            .firstTransactionSignature(firstSwap.getTransactionSignature())
            .resultMessage("Second swap failed: " + secondSwap.getErrorMessage())
            .build();
      }

      // Calculate actual profit
      BigInteger finalOutput = BigInteger.valueOf(secondSwap.getTokenEstOut());
      BigInteger profit = finalOutput.subtract(opportunity.getInputAmount());
      boolean isProfit = profit.compareTo(BigInteger.ZERO) > 0;

      String resultMessage =
          isProfit
              ? String.format(
                  "Profit: %s (%s bps)",
                  profit,
                  profit.multiply(BigInteger.valueOf(10000)).divide(opportunity.getInputAmount()))
              : String.format("Loss: %s", profit.abs());

      return OrcArbitrageResult.builder()
          .success(isProfit)
          .firstTransactionSignature(firstSwap.getTransactionSignature())
          .secondTransactionSignature(secondSwap.getTransactionSignature())
          .resultMessage(resultMessage)
          .build();

    } catch (Exception e) {
      log.error("Error executing arbitrage: {}", e.getMessage(), e);
      return OrcArbitrageResult.builder()
          .success(false)
          .resultMessage("Error: " + e.getMessage())
          .build();
    }
  }

  /**
   * Creates a token account for a given mint.
   *
   * @param mintAddress The mint address to create a token account for
   * @return true if successful
   */
  public boolean createTokenAccount(String mintAddress) {
    try {
      Signer signer = Signer.createFromPrivateKey(Base58.decode(MY_WALLET_PRIVATE_KEY));
      tokenAccountService.createAssociatedTokenAccount(MY_WALLET_ADDRESS, mintAddress, signer);
      return true;
    } catch (Exception e) {
      log.error("Error creating token account: {}", e.getMessage(), e);
      return false;
    }
  }

  /** Checks for an arbitrage opportunity between two pools. */
  private OrcArbitrageOpportunity checkArbitrageForPoolPair(
      String firstPoolAddress,
      String secondPoolAddress,
      BigInteger inputAmount,
      String inputTokenMint) {

    try {
      // 1. Fetch both pools
      OrcWhirlpool firstPool = accountFetcher.fetchWhirlpool(firstPoolAddress);
      OrcWhirlpool secondPool = accountFetcher.fetchWhirlpool(secondPoolAddress);

      // 2. Verify token compatibility for arbitrage
      boolean isCompatible = isPoolsCompatibleForArbitrage(firstPool, secondPool, inputTokenMint);
      if (!isCompatible) {
        return null;
      }

      // 3. Determine intermediate token and directions
      String intermediateToken = determineIntermediateToken(firstPool, inputTokenMint);
      if (intermediateToken == null) {
        return null;
      }

      boolean aToB1 = firstPool.getTokenMintA().equals(inputTokenMint);

      // 4. Calculate first swap (exact input)
      OrcSwapQuote firstSwapQuote =
          calculateSwapQuote(inputAmount, inputTokenMint, firstPool, aToB1, OrcSwapType.EXACT_IN);

      // 5. Calculate second swap (exact input with output from first swap)
      boolean aToB2 = secondPool.getTokenMintA().equals(intermediateToken);

      OrcSwapQuote secondSwapQuote =
          calculateSwapQuote(
              firstSwapQuote.getEstimatedAmountOut(),
              intermediateToken,
              secondPool,
              aToB2,
              OrcSwapType.EXACT_IN);

      // 6. Check if profitable
      BigInteger fees = OrcConstants.DEFAULT_GAS_ESTIMATE.multiply(BigInteger.valueOf(2));
      BigInteger netOutput = secondSwapQuote.getEstimatedAmountOut().subtract(fees);
      BigInteger profit = netOutput.subtract(inputAmount);

      // Calculate profit in basis points
      int profitBps = 0;
      if (profit.compareTo(BigInteger.ZERO) > 0) {
        profitBps = profit.multiply(BigInteger.valueOf(10000)).divide(inputAmount).intValue();
      }

      if (profit.compareTo(BigInteger.ZERO) > 0) {
        return OrcArbitrageOpportunity.builder()
            .firstPoolAddress(firstPoolAddress)
            .secondPoolAddress(secondPoolAddress)
            .inputAmount(inputAmount)
            .inputToken(inputTokenMint)
            .firstPoolDirection(aToB1 ? "A->B" : "B->A")
            .intermediateAmount(firstSwapQuote.getEstimatedAmountOut())
            .intermediateToken(intermediateToken)
            .secondPoolDirection(aToB2 ? "A->B" : "B->A")
            .outputAmount(secondSwapQuote.getEstimatedAmountOut())
            .profitAmount(profit)
            .profitBasisPoints(profitBps)
            .build();
      }

    } catch (Exception e) {
      log.error("Error checking arbitrage for pools: {}", e.getMessage(), e);
    }

    return null;
  }

  /** Verifies that two pools are compatible for arbitrage. */
  private boolean isPoolsCompatibleForArbitrage(
      OrcWhirlpool pool1, OrcWhirlpool pool2, String inputTokenMint) {

    // Check that input token is present in first pool
    boolean inputInPool1 =
        pool1.getTokenMintA().equals(inputTokenMint)
            || pool1.getTokenMintB().equals(inputTokenMint);

    if (!inputInPool1) {
      return false;
    }

    // Check that the two pools share at least one token
    return pool1.getTokenMintA().equals(pool2.getTokenMintA())
        || pool1.getTokenMintA().equals(pool2.getTokenMintB())
        || pool1.getTokenMintB().equals(pool2.getTokenMintA())
        || pool1.getTokenMintB().equals(pool2.getTokenMintB());
  }

  /** Determines the intermediate token for an arbitrage between two pools. */
  private String determineIntermediateToken(OrcWhirlpool pool, String inputToken) {
    // Return the other token in the pair
    if (pool.getTokenMintA().equals(inputToken)) {
      return pool.getTokenMintB();
    } else if (pool.getTokenMintB().equals(inputToken)) {
      return pool.getTokenMintA();
    }

    return null;
  }

  /** Calculates a swap quote for a given pool and direction. */
  private OrcSwapQuote calculateSwapQuote(
      BigInteger amount, String inputToken, OrcWhirlpool pool, boolean aToB, OrcSwapType swapType) {

    log.debug(
        "Calculating swap quote - input amount: {}, token: {}, pool: {}, aToB: {}",
        amount,
        inputToken,
        pool.getAddress(),
        aToB);
    log.debug(
        "Pool details - liquidity: {}, sqrtPrice: {}, feeRate: {}",
        pool.getLiquidity(),
        pool.getSqrtPrice(),
        pool.getFeeRate());

    OrcSwapQuote quote = new OrcSwapQuote();

    if (swapType == OrcSwapType.EXACT_IN) {
      // Apply fee rate to input amount
      BigInteger amountInAfterFee = OrcMathUtils.applyFeeRate(amount, pool.getFeeRate());
      log.debug("Amount after fee: {}", amountInAfterFee);

      // Calculate next sqrt price
      BigInteger currentSqrtPrice = pool.getSqrtPrice();
      BigInteger nextSqrtPrice;
      BigInteger amountOut;

      if (aToB) {
        // A to B swap (price goes down)
        nextSqrtPrice =
            OrcMathUtils.getNextSqrtPriceFromTokenAInput(
                currentSqrtPrice, pool.getLiquidity(), amountInAfterFee, true);
        log.debug("A->B swap, next sqrt price: {}", nextSqrtPrice);

        amountOut =
            OrcMathUtils.getTokenBDelta(pool.getLiquidity(), nextSqrtPrice, currentSqrtPrice);
      } else {
        // B to A swap (price goes up)
        nextSqrtPrice =
            OrcMathUtils.getNextSqrtPriceFromTokenBInput(
                currentSqrtPrice, pool.getLiquidity(), amountInAfterFee, true);
        log.debug("B->A swap, next sqrt price: {}", nextSqrtPrice);

        amountOut =
            OrcMathUtils.getTokenADelta(pool.getLiquidity(), currentSqrtPrice, nextSqrtPrice);
      }

      log.debug("Calculated output amount: {}", amountOut);

      // Calculate minimum output with 0.5% slippage
      BigInteger minAmountOut =
          amountOut.multiply(BigInteger.valueOf(9950)).divide(BigInteger.valueOf(10000));

      quote.setTokenIn(amount);
      quote.setEstimatedAmountIn(amount);
      quote.setTokenMaxIn(amount);
      quote.setEstimatedAmountOut(amountOut);
      quote.setTokenMinOut(minAmountOut);

      log.debug("Quote complete - estimatedOut: {}, minOut: {}", amountOut, minAmountOut);
    } else {
      // Exact output swap - simplified implementation
      throw new UnsupportedOperationException("Exact output quotes not implemented");
    }

    return quote;
  }

  /** Optimizes the amount for an arbitrage opportunity. */
  private OrcArbitrageOpportunity optimizeArbitrageAmount(
      OrcArbitrageOpportunity baseOpportunity, BigInteger minAmount, BigInteger maxAmount) {

    // If min and max are close, just return the base opportunity
    if (maxAmount.subtract(minAmount).compareTo(minAmount.divide(BigInteger.TEN)) <= 0) {
      return baseOpportunity;
    }

    log.info("Optimizing arbitrage amount between {} and {}", minAmount, maxAmount);

    // Try with mid amount using binary search
    BigInteger midAmount = minAmount.add(maxAmount).divide(BigInteger.valueOf(2));

    // Try with the mid amount
    OrcArbitrageOpportunity midOpportunity =
        checkArbitrageForPoolPair(
            baseOpportunity.getFirstPoolAddress(),
            baseOpportunity.getSecondPoolAddress(),
            midAmount,
            baseOpportunity.getInputToken());

    // If mid opportunity is better, recurse with upper half
    if (midOpportunity != null
        && midOpportunity.getProfitBasisPoints() > baseOpportunity.getProfitBasisPoints()) {
      return optimizeArbitrageAmount(midOpportunity, midAmount, maxAmount);
    } else {
      // Otherwise, recurse with lower half
      return optimizeArbitrageAmount(baseOpportunity, minAmount, midAmount);
    }
  }

  /** Returns the decimals for common token mints. */
  int getTokenDecimals(String tokenMint) {
    // Common token decimals
    if (OrcConstants.USDC_MINT.equals(tokenMint) || OrcConstants.USDT_MINT.equals(tokenMint)) {
      return 6;
    } else if (OrcConstants.WSOL_MINT.equals(tokenMint)) {
      return 9;
    }

    // Default for unknown tokens
    return 9;
  }

  // In your OrcWhirlpoolService/OrcWhirlpoolManagementService class
  public OrcArbitrageOpportunity findOptimalArbitrageOpportunity(
      List<String> poolAddresses, String tokenMint, int minProfitBps) {

    // Get all possible pool pairs
    List<OrcPoolPair> poolPairs = whirlpoolManagementService.generatePoolPairs(poolAddresses);

    // Store best opportunity
    OrcArbitrageOpportunity bestOpportunity = null;
    int highestProfitBps = 0;

    // Try different input amounts to find optimal
    BigInteger[] amountsToTry = {
      BigInteger.valueOf(1_000_000), // 1 USDC
      BigInteger.valueOf(10_000_000), // 10 USDC
      BigInteger.valueOf(100_000_000), // 100 USDC
      BigInteger.valueOf(1_000_000_000) // 1000 USDC
    };

    for (OrcPoolPair pair : poolPairs) {
      for (BigInteger amount : amountsToTry) {
        OrcArbitrageOpportunity opp =
            checkArbitrageForPoolPair(pair.getFirstPool(), pair.getSecondPool(), amount, tokenMint);

        if (opp != null && opp.getProfitBasisPoints() > highestProfitBps) {
          bestOpportunity = opp;
          highestProfitBps = opp.getProfitBasisPoints();
        }
      }
    }

    // Only return if we meet the minimum profit threshold
    if (bestOpportunity != null && bestOpportunity.getProfitBasisPoints() >= minProfitBps) {
      return bestOpportunity;
    }

    return null;
  }

  // New method for your OrcWhirlpoolService
  public OrcArbitrageResult executeArbitrageWithAtomicTransaction(
      OrcArbitrageOpportunity opportunity) {
    try {
      // Create signer from private key
      Signer signer = Signer.createFromPrivateKey(Base58.decode(MY_WALLET_PRIVATE_KEY));

      // Create instructions for first swap
      List<Instruction> instructions = new ArrayList<>();

      // Build first swap instruction
      Instruction firstSwapInstruction =
          swapService.buildSwapInstruction(
              opportunity.getFirstPoolAddress(),
              opportunity.getInputAmount(),
              opportunity.getInputToken(),
              OrcSwapType.EXACT_IN,
              50, // 0.5% slippage
              MY_WALLET_ADDRESS,
              signer);

      instructions.add(firstSwapInstruction);

      // Add second swap instruction
      // This requires knowing the output token account from first swap
      String intermediateTokenAccount =
          tokenAccountService.getOrCreateTokenAccount(
              MY_WALLET_ADDRESS, opportunity.getIntermediateToken(), signer);

      Instruction secondSwapInstruction =
          swapService.buildSwapInstruction(
              opportunity.getSecondPoolAddress(),
              opportunity.getIntermediateAmount(),
              opportunity.getIntermediateToken(),
              OrcSwapType.EXACT_IN,
              50, // 0.5% slippage
              MY_WALLET_ADDRESS,
              signer);

      instructions.add(secondSwapInstruction);

      // Execute as a single transaction (atomic)
      String txSignature = transactionService.sendTransaction(instructions, signer);

      return OrcArbitrageResult.builder()
          .success(true)
          .firstTransactionSignature(txSignature)
          .resultMessage("Executed atomic arbitrage")
          .build();

    } catch (Exception e) {
      log.error("Error executing atomic arbitrage: {}", e.getMessage(), e);
      return OrcArbitrageResult.builder()
          .success(false)
          .resultMessage("Error: " + e.getMessage())
          .build();
    }
  }

  // In OrcSwapService, add this method to build instruction without executing
  public Instruction buildSwapInstruction(
      String poolAddress,
      BigInteger amount,
      String inputMint,
      OrcSwapType swapType,
      int slippageToleranceBps,
      String walletAddress,
      Signer signer)
      throws Exception {

    // Get the pool data
    OrcWhirlpool pool = accountFetcher.fetchWhirlpool(poolAddress);

    // Determine swap direction
    boolean isInputTokenA = pool.getTokenMintA().equals(inputMint);
    boolean aToB = (swapType == OrcSwapType.EXACT_IN) == isInputTokenA;

    // Get token accounts
    String userTokenAccountA =
        tokenAccountService.getOrCreateTokenAccount(walletAddress, pool.getTokenMintA(), signer);
    String userTokenAccountB =
        tokenAccountService.getOrCreateTokenAccount(walletAddress, pool.getTokenMintB(), signer);

    // Get tick arrays
    String[] tickArrayAddresses =
        OrcAddressUtils.getTickArrayAddressesForSwap(
            poolAddress, pool.getTickCurrentIndex(), pool.getTickSpacing(), aToB);

    // Calculate swap quote
    OrcSwapQuote quote;
    if (swapType == OrcSwapType.EXACT_IN) {
      quote = swapService.calculateExactInSwapQuote(amount, aToB, slippageToleranceBps, pool);
    } else {
      quote = swapService.calculateExactOutSwapQuote(amount, aToB, slippageToleranceBps, pool);
    }

    // Build instruction data
    byte[] instructionData =
        swapService.createSwapInstructionData(
            amount,
            swapType == OrcSwapType.EXACT_IN ? quote.getTokenMinOut() : quote.getTokenMaxIn(),
            BigInteger.ZERO, // No price limit
            swapType == OrcSwapType.EXACT_IN,
            aToB);

    // Get oracle address
    String oracleAddress = OrcAddressUtils.getOracleAddress(poolAddress);

    // Build accounts
    List<AccountMeta> accounts =
        swapService.buildSwapAccounts(
            poolAddress,
            pool,
            userTokenAccountA,
            userTokenAccountB,
            tickArrayAddresses,
            oracleAddress,
            walletAddress);

    // Return instruction without executing
    return Instruction.createInstruction(
        OrcConstants.WHIRLPOOL_PROGRAM_ID, accounts, instructionData);
  }
}
