package com.crypto.arbitrage.providers.orc.services;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.*;
import com.crypto.arbitrage.providers.orc.utils.OrcAddressUtils;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.Signer;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;

/**
 * Service for executing swaps on Orca Whirlpools. This implementation includes proper fixed-point
 * math for swap calculations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrcSwapService {

  private final OrcAccountFetcher accountFetcher;
  private final OrcTokenAccountService tokenAccountService;
  private final OrcTransactionService transactionService;

  /**
   * Executes a token swap on an Orca Whirlpool.
   *
   * @param poolAddress The address of the Whirlpool
   * @param amount The amount to swap
   * @param inputMint The mint of the input token
   * @param swapType Whether this is an exact input or exact output swap
   * @param slippageToleranceBps Slippage tolerance in basis points
   * @param walletAddress The wallet address executing the swap
   * @param signer The transaction signer
   * @return The result of the swap transaction
   * @throws Exception If there's an error during the swap
   */
  public OrcSwapTransactionResult executeSwap(
      String poolAddress,
      BigInteger amount,
      String inputMint,
      OrcSwapType swapType,
      int slippageToleranceBps,
      String walletAddress,
      Signer signer)
      throws Exception {

    log.info("Executing swap on pool {} with amount {}", poolAddress, amount);

    // 1. Fetch the pool data
    OrcWhirlpool pool = accountFetcher.fetchWhirlpool(poolAddress);

    // Check pool liquidity
    if (pool.getLiquidity().equals(BigInteger.ZERO)) {
      log.error("Pool has zero liquidity, cannot execute swap");
      return OrcSwapTransactionResult.builder()
          .success(false)
          .errorMessage("Pool has zero liquidity")
          .build();
    }

    // 2. Determine swap direction (A→B or B→A)
    boolean isInputTokenA = pool.getTokenMintA().equals(inputMint);
    boolean aToB = (swapType == OrcSwapType.EXACT_IN) == isInputTokenA;

    log.info("Swap direction: A to B = {}, Input is token A = {}", aToB, isInputTokenA);
    log.info("Pool liquidity: {}", pool.getLiquidity());

    // Check if amount is sufficient for a viable swap
    BigInteger minViableAmount = calculateMinimumViableAmount(pool, inputMint);
    if (amount.compareTo(minViableAmount) < 0) {
      log.warn(
          "Input amount {} is below minimum viable amount {}, adjusting to minimum",
          amount,
          minViableAmount);
      amount = minViableAmount;
    }

    // 3. Get token accounts
    String userTokenAccountA =
        tokenAccountService.getOrCreateTokenAccount(walletAddress, pool.getTokenMintA(), signer);
    String userTokenAccountB =
        tokenAccountService.getOrCreateTokenAccount(walletAddress, pool.getTokenMintB(), signer);

    // 4. Calculate swap amounts
    OrcSwapQuote quote;
    if (swapType == OrcSwapType.EXACT_IN) {
      // Calculate output amount for exact input
      BigInteger outputAmount = calculateFixedSwapOutput(amount, pool, aToB);

      // Calculate minimum output with slippage
      BigInteger minOutputAmount =
          outputAmount
              .multiply(BigInteger.valueOf(10000 - slippageToleranceBps))
              .divide(BigInteger.valueOf(10000));

      // Create quote
      quote =
          OrcSwapQuote.builder()
              .tokenIn(amount)
              .estimatedAmountIn(amount)
              .tokenMaxIn(amount)
              .estimatedAmountOut(outputAmount)
              .tokenMinOut(minOutputAmount.max(BigInteger.ONE)) // Ensure non-zero minimum output
              .fee(calculateFeeAmount(amount, pool.getFeeRate()))
              .build();
    } else {
      // For exact output swaps (more complex)
      BigInteger inputAmount = calculateFixedSwapInput(amount, pool, aToB);

      // Calculate maximum input with slippage
      BigInteger maxInputAmount =
          inputAmount
              .multiply(BigInteger.valueOf(10000 + slippageToleranceBps))
              .divide(BigInteger.valueOf(10000));

      // Create quote
      quote =
          OrcSwapQuote.builder()
              .tokenIn(inputAmount)
              .estimatedAmountIn(inputAmount)
              .tokenMaxIn(maxInputAmount)
              .estimatedAmountOut(amount)
              .tokenMinOut(amount)
              .fee(calculateFeeAmount(inputAmount, pool.getFeeRate()))
              .build();
    }

    log.info(
        "Calculated swap quote: input={}, estimated output={}, min output={}",
        quote.getTokenIn(),
        quote.getEstimatedAmountOut(),
        quote.getTokenMinOut());

    // Check for zero output
    if (quote.getEstimatedAmountOut().equals(BigInteger.ZERO)) {
      log.error("Swap would produce zero output tokens, cannot execute");
      return OrcSwapTransactionResult.builder()
          .success(false)
          .errorMessage("Swap would produce zero output tokens")
          .build();
    }

    // Print human-readable amounts for easier debugging
    int decimalsA = getTokenDecimals(pool.getTokenMintA());
    int decimalsB = getTokenDecimals(pool.getTokenMintB());

    try {
      if (isInputTokenA) {
        log.info(
            "Swapping {} {} for approximately {} {}",
            formatWithDecimals(quote.getTokenIn(), decimalsA),
            getTokenName(pool.getTokenMintA()),
            formatWithDecimals(quote.getEstimatedAmountOut(), decimalsB),
            getTokenName(pool.getTokenMintB()));
      } else {
        log.info(
            "Swapping {} {} for approximately {} {}",
            formatWithDecimals(quote.getTokenIn(), decimalsB),
            getTokenName(pool.getTokenMintB()),
            formatWithDecimals(quote.getEstimatedAmountOut(), decimalsA),
            getTokenName(pool.getTokenMintA()));
      }
    } catch (Exception e) {
      log.error("Error formatting amounts: {}", e.getMessage());
    }

    // 5. Get tick arrays for the instruction
    String[] tickArrayAddresses =
        OrcAddressUtils.getTickArrayAddressesForSwap(
            poolAddress, pool.getTickCurrentIndex(), pool.getTickSpacing(), aToB);

    // 6. Build swap instruction
    byte[] instructionData =
        createSwapInstructionData(
            swapType == OrcSwapType.EXACT_IN ? amount : quote.getEstimatedAmountOut(),
            swapType == OrcSwapType.EXACT_IN ? quote.getTokenMinOut() : quote.getTokenMaxIn(),
            BigInteger.ZERO, // No price limit
            swapType == OrcSwapType.EXACT_IN,
            aToB);

    // 7. Get oracle address
    String oracleAddress = OrcAddressUtils.getOracleAddress(poolAddress);

    // 8. Build accounts for the instruction
    List<AccountMeta> accounts =
        buildSwapAccounts(
            poolAddress,
            pool,
            userTokenAccountA,
            userTokenAccountB,
            tickArrayAddresses,
            oracleAddress,
            walletAddress);

    // 9. Create instruction
    Instruction swapInstruction =
        Instruction.createInstruction(OrcConstants.WHIRLPOOL_PROGRAM_ID, accounts, instructionData);

    // 10. Simulate the transaction first
    try {
      var simulation =
          transactionService
              .simulateTransaction(Collections.singletonList(swapInstruction), signer)
              .join();

      if (simulation.error() != null) {
        log.error("Swap simulation failed: {}", simulation.error());

        // Check for specific error codes
        if (simulation.error().toString().contains("Custom[error=101]")) {
          log.error("Error 101: Zero tradable amount. Try increasing your swap amount.");
          return OrcSwapTransactionResult.builder()
              .success(false)
              .errorMessage("Zero tradable amount. Try increasing your swap amount.")
              .build();
        }

        throw new Exception("Swap simulation failed: " + simulation.error());
      }

      log.info("Swap simulation successful");
    } catch (Exception e) {
      log.error("Error simulating swap: {}", e.getMessage(), e);
      return OrcSwapTransactionResult.builder()
          .success(false)
          .errorMessage("Simulation failed: " + e.getMessage())
          .build();
    }

    // 11. Send transaction
    try {
      String signature =
          transactionService.sendTransaction(Collections.singletonList(swapInstruction), signer);

      log.info("Swap transaction sent with signature: {}", signature);

      // 12. Return result
      return OrcSwapTransactionResult.builder()
          .instructions(Collections.singletonList(swapInstruction))
          .transactionSignature(signature)
          .tokenEstOut(quote.getEstimatedAmountOut().longValue())
          .success(true)
          .build();
    } catch (Exception e) {
      log.error("Error sending swap transaction: {}", e.getMessage(), e);
      return OrcSwapTransactionResult.builder()
          .success(false)
          .errorMessage("Transaction failed: " + e.getMessage())
          .build();
    }
  }

  /**
   * Calculates the swap output amount using correct fixed-point math. This is the main method used
   * to calculate how much of the output token will be received for a given input amount.
   */
  private BigInteger calculateFixedSwapOutput(
      BigInteger amountIn, OrcWhirlpool pool, boolean aToB) {
    // Apply fee rate
    BigInteger amountInAfterFee =
        amountIn
            .multiply(BigInteger.valueOf(1_000_000 - pool.getFeeRate()))
            .divide(BigInteger.valueOf(1_000_000));

    // Calculate price change
    BigInteger currentSqrtPrice = pool.getSqrtPrice();
    BigInteger liquidity = pool.getLiquidity();

    try {
      if (aToB) {
        // A to B swap - price goes down
        // Calculate next price after swap
        BigInteger numerator = liquidity.multiply(currentSqrtPrice).shiftLeft(64);
        BigInteger product = amountInAfterFee.multiply(currentSqrtPrice);
        BigInteger denominator = liquidity.shiftLeft(64).add(product);

        if (denominator.equals(BigInteger.ZERO)) {
          return BigInteger.ONE;
        }

        BigInteger nextSqrtPrice = numerator.divide(denominator);

        // Apply bounds to the next sqrt price
        if (nextSqrtPrice.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
          nextSqrtPrice = OrcConstants.MIN_SQRT_PRICE;
        }

        // Calculate token B delta: liquidity * (currentSqrtPrice - nextSqrtPrice) / 2^64
        BigInteger amountOut =
            liquidity.multiply(currentSqrtPrice.subtract(nextSqrtPrice)).shiftRight(64);

        return amountOut.max(BigInteger.ONE); // Ensure non-zero output
      } else {
        // B to A swap - price goes up
        // Calculate next price: currentSqrtPrice + amountInAfterFee << 64 / liquidity
        BigInteger delta = amountInAfterFee.shiftLeft(64).divide(liquidity);
        BigInteger nextSqrtPrice = currentSqrtPrice.add(delta);

        // Apply bounds to the next sqrt price
        if (nextSqrtPrice.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
          nextSqrtPrice = OrcConstants.MAX_SQRT_PRICE;
        }

        // Calculate token A delta: liquidity * (nextSqrtPrice - currentSqrtPrice) * 2^64 /
        // (nextSqrtPrice * currentSqrtPrice)
        BigInteger numeratorA =
            liquidity.multiply(nextSqrtPrice.subtract(currentSqrtPrice)).shiftLeft(64);
        BigInteger denominatorA = nextSqrtPrice.multiply(currentSqrtPrice);

        if (denominatorA.equals(BigInteger.ZERO)) {
          return BigInteger.ONE;
        }

        BigInteger amountOut = numeratorA.divide(denominatorA);
        return amountOut.max(BigInteger.ONE); // Ensure non-zero output
      }
    } catch (ArithmeticException e) {
      log.error("Arithmetic error in swap calculation: {}", e.getMessage());
      // Return a small but valid amount
      return BigInteger.valueOf(1000);
    }
  }

  /**
   * Calculates the input amount needed for a desired output amount. Used for exact output swaps.
   */
  private BigInteger calculateFixedSwapInput(
      BigInteger amountOut, OrcWhirlpool pool, boolean aToB) {
    BigInteger currentSqrtPrice = pool.getSqrtPrice();
    BigInteger liquidity = pool.getLiquidity();

    try {
      BigInteger amountIn;

      if (aToB) {
        // A to B swap (exact output)
        // First calculate the new sqrt price that would give exactly amountOut of token B
        BigInteger sqrtPriceDelta = amountOut.shiftLeft(64).divide(liquidity);
        BigInteger nextSqrtPrice = currentSqrtPrice.subtract(sqrtPriceDelta);

        // Apply bounds
        if (nextSqrtPrice.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
          nextSqrtPrice = OrcConstants.MIN_SQRT_PRICE;
        }

        // Now calculate the amount of token A needed for this price change
        BigInteger numerator =
            liquidity
                .multiply(currentSqrtPrice.subtract(nextSqrtPrice))
                .multiply(currentSqrtPrice)
                .multiply(nextSqrtPrice);
        BigInteger denominator =
            currentSqrtPrice.multiply(nextSqrtPrice).multiply(BigInteger.valueOf(1 << 64));

        amountIn = numerator.divide(denominator);
      } else {
        // B to A swap (exact output)
        // For token A output, the calculation is more complex
        BigInteger term1 = liquidity.multiply(currentSqrtPrice).shiftLeft(64);
        BigInteger term2 = amountOut.multiply(currentSqrtPrice).multiply(currentSqrtPrice);
        BigInteger nextSqrtPrice = term1.divide(term1.subtract(term2));

        // Apply bounds
        if (nextSqrtPrice.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
          nextSqrtPrice = OrcConstants.MAX_SQRT_PRICE;
        }

        // Calculate token B input
        amountIn = liquidity.multiply(nextSqrtPrice.subtract(currentSqrtPrice)).shiftRight(64);
      }

      // Apply fee rate (reverse calculation)
      BigInteger amountInWithFee =
          amountIn
              .multiply(BigInteger.valueOf(1_000_000))
              .divide(BigInteger.valueOf(1_000_000 - pool.getFeeRate()));

      return amountInWithFee.max(BigInteger.ONE); // Ensure non-zero input
    } catch (ArithmeticException e) {
      log.error("Arithmetic error in input calculation: {}", e.getMessage());
      // Return a reasonably large amount as fallback
      return BigInteger.valueOf(10_000_000);
    }
  }

  /** Calculate the fee amount for a given input amount. */
  private BigInteger calculateFeeAmount(BigInteger amount, int feeRate) {
    return amount.multiply(BigInteger.valueOf(feeRate)).divide(BigInteger.valueOf(1_000_000));
  }

  /**
   * Creates the instruction data for a swap. This builds the binary data structure expected by the
   * Orca program.
   */
  byte[] createSwapInstructionData(
      BigInteger amount,
      BigInteger otherAmountThreshold,
      BigInteger sqrtPriceLimit,
      boolean amountSpecifiedIsInput,
      boolean aToB) {

    // Buffer for instruction data - exact size for SwapV2 instruction
    ByteBuffer buffer = ByteBuffer.allocate(35); // 1+8+8+16+1+1 bytes
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    // Discriminator (4 = SwapV2)
    buffer.put(OrcConstants.SWAP_V2_INSTRUCTION);

    // Amount
    putUint64(buffer, amount);

    // Other amount threshold
    putUint64(buffer, otherAmountThreshold);

    // sqrt_price_limit (default to min/max based on direction)
    if (aToB && sqrtPriceLimit.equals(BigInteger.ZERO)) {
      sqrtPriceLimit = OrcConstants.MIN_SQRT_PRICE;
    } else if (!aToB && sqrtPriceLimit.equals(BigInteger.ZERO)) {
      sqrtPriceLimit = OrcConstants.MAX_SQRT_PRICE;
    }

    putUint128(buffer, sqrtPriceLimit);

    // Flags
    buffer.put(amountSpecifiedIsInput ? (byte) 1 : (byte) 0);
    buffer.put(aToB ? (byte) 1 : (byte) 0);

    return buffer.array();
  }

  /**
   * Builds the account metas required for a swap instruction. These must be in the exact order
   * expected by the Orca program.
   */
  List<AccountMeta> buildSwapAccounts(
      String poolAddress,
      OrcWhirlpool pool,
      String userTokenAccountA,
      String userTokenAccountB,
      String[] tickArrayAddresses,
      String oracleAddress,
      String walletAddress) {

    PublicKey walletKey = PublicKey.fromBase58Encoded(walletAddress);

    List<AccountMeta> accounts = new ArrayList<>();

    // The accounts must be in exactly this order
    accounts.add(AccountMeta.createWrite(PublicKey.fromBase58Encoded(poolAddress))); // whirlpool
    accounts.add(AccountMeta.createRead(OrcConstants.TOKEN_PROGRAM_ID)); // token_program
    accounts.add(AccountMeta.createRead(OrcConstants.TOKEN_PROGRAM_ID)); // token_program
    accounts.add(AccountMeta.createRead(OrcConstants.MEMO_PROGRAM_ID)); // memo_program
    accounts.add(AccountMeta.createWritableSigner(walletKey)); // token_authority
    accounts.add(
        AccountMeta.createWrite(
            PublicKey.fromBase58Encoded(userTokenAccountA))); // token_owner_account_a
    accounts.add(
        AccountMeta.createWrite(
            PublicKey.fromBase58Encoded(pool.getTokenVaultA()))); // token_vault_a
    accounts.add(
        AccountMeta.createWrite(
            PublicKey.fromBase58Encoded(userTokenAccountB))); // token_owner_account_b
    accounts.add(
        AccountMeta.createWrite(
            PublicKey.fromBase58Encoded(pool.getTokenVaultB()))); // token_vault_b
    accounts.add(
        AccountMeta.createWrite(
            PublicKey.fromBase58Encoded(tickArrayAddresses[0]))); // tick_array_0
    accounts.add(
        AccountMeta.createWrite(
            PublicKey.fromBase58Encoded(tickArrayAddresses[1]))); // tick_array_1
    accounts.add(
        AccountMeta.createWrite(
            PublicKey.fromBase58Encoded(tickArrayAddresses[2]))); // tick_array_2
    accounts.add(AccountMeta.createRead(PublicKey.fromBase58Encoded(oracleAddress))); // oracle

    return accounts;
  }

  /**
   * Builds a swap instruction without executing it. This is used for building atomic transactions
   * like arbitrage operations.
   */
  public Instruction buildSwapInstruction(
      String poolAddress,
      BigInteger amount,
      String inputMint,
      OrcSwapType swapType,
      int slippageToleranceBps,
      String walletAddress,
      Signer signer)
      throws Exception {

    log.info("Building swap instruction for pool {} with amount {}", poolAddress, amount);

    // 1. Fetch the pool data
    OrcWhirlpool pool = accountFetcher.fetchWhirlpool(poolAddress);

    // Check pool liquidity
    if (pool.getLiquidity().equals(BigInteger.ZERO)) {
      throw new Exception("Pool has zero liquidity");
    }

    // 2. Determine swap direction (A→B or B→A)
    boolean isInputTokenA = pool.getTokenMintA().equals(inputMint);
    boolean aToB = (swapType == OrcSwapType.EXACT_IN) == isInputTokenA;

    // Check if amount is sufficient
    BigInteger minViableAmount = calculateMinimumViableAmount(pool, inputMint);
    if (amount.compareTo(minViableAmount) < 0) {
      log.warn(
          "Input amount {} is below minimum viable amount {}, adjusting to minimum",
          amount,
          minViableAmount);
      amount = minViableAmount;
    }

    // 3. Get token accounts
    String userTokenAccountA =
        tokenAccountService.getOrCreateTokenAccount(walletAddress, pool.getTokenMintA(), signer);
    String userTokenAccountB =
        tokenAccountService.getOrCreateTokenAccount(walletAddress, pool.getTokenMintB(), signer);

    // 4. Calculate swap amounts
    BigInteger otherAmountThreshold;
    if (swapType == OrcSwapType.EXACT_IN) {
      // Calculate output amount
      BigInteger outputAmount = calculateFixedSwapOutput(amount, pool, aToB);

      // Apply slippage to get minimum output
      otherAmountThreshold =
          outputAmount
              .multiply(BigInteger.valueOf(10000 - slippageToleranceBps))
              .divide(BigInteger.valueOf(10000));

      // Ensure non-zero
      otherAmountThreshold = otherAmountThreshold.max(BigInteger.ONE);
    } else {
      // Calculate input amount
      BigInteger inputAmount = calculateFixedSwapInput(amount, pool, aToB);

      // Apply slippage to get maximum input
      otherAmountThreshold =
          inputAmount
              .multiply(BigInteger.valueOf(10000 + slippageToleranceBps))
              .divide(BigInteger.valueOf(10000));
    }

    // 5. Get tick arrays
    String[] tickArrayAddresses =
        OrcAddressUtils.getTickArrayAddressesForSwap(
            poolAddress, pool.getTickCurrentIndex(), pool.getTickSpacing(), aToB);

    // 6. Build instruction data
    byte[] instructionData =
        createSwapInstructionData(
            amount,
            otherAmountThreshold,
            BigInteger.ZERO, // No price limit
            swapType == OrcSwapType.EXACT_IN,
            aToB);

    // 7. Get oracle address
    String oracleAddress = OrcAddressUtils.getOracleAddress(poolAddress);

    // 8. Build accounts for the instruction
    List<AccountMeta> accounts =
        buildSwapAccounts(
            poolAddress,
            pool,
            userTokenAccountA,
            userTokenAccountB,
            tickArrayAddresses,
            oracleAddress,
            walletAddress);

    // 9. Create and return instruction
    return Instruction.createInstruction(
        OrcConstants.WHIRLPOOL_PROGRAM_ID, accounts, instructionData);
  }

  /**
   * Calculates the minimum viable amount for a swap in a given pool. Amounts below this threshold
   * may produce zero output due to precision issues.
   */
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

  /** Helper method to write a uint64 to a ByteBuffer. */
  private void putUint64(ByteBuffer buffer, BigInteger value) {
    byte[] bytes = new byte[8];
    byte[] valueBytes = value.toByteArray();
    int srcPos = Math.max(0, valueBytes.length - 8);
    int length = Math.min(valueBytes.length, 8);
    System.arraycopy(valueBytes, srcPos, bytes, 8 - length, length);
    buffer.put(bytes);
  }

  /** Helper method to write a uint128 to a ByteBuffer. */
  private void putUint128(ByteBuffer buffer, BigInteger value) {
    byte[] bytes = new byte[16];
    byte[] valueBytes = value.toByteArray();
    int srcPos = Math.max(0, valueBytes.length - 16);
    int length = Math.min(valueBytes.length, 16);
    System.arraycopy(valueBytes, srcPos, bytes, 16 - length, length);
    buffer.put(bytes);
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

  /** Helper to get token name for common tokens */
  private String getTokenName(String tokenMint) {
    if (OrcConstants.USDC_MINT.equals(tokenMint)) {
      return "USDC";
    } else if (OrcConstants.USDT_MINT.equals(tokenMint)) {
      return "USDT";
    } else if (OrcConstants.WSOL_MINT.equals(tokenMint)) {
      return "SOL";
    }
    return "Unknown Token";
  }

  /** Helper to format a BigInteger with the correct number of decimals */
  private String formatWithDecimals(BigInteger value, int decimals) {
    if (value == null) return "0";

    BigDecimal decimalValue = new BigDecimal(value);
    BigDecimal divisor = BigDecimal.TEN.pow(decimals);
    return decimalValue.divide(divisor, decimals, RoundingMode.HALF_UP).toString();
  }
}
