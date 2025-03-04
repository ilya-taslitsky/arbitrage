package com.crypto.arbitrage.providers.orc.services;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.*;
import com.crypto.arbitrage.providers.orc.utils.*;

import java.math.BigInteger;
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

package com.crypto.arbitrage.providers.orc.services;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.*;
import com.crypto.arbitrage.providers.orc.utils.OrcAddressUtils;
import com.crypto.arbitrage.providers.orc.utils.OrcMathUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
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
 * Service for executing swaps on Orca Whirlpools.
 * This implementation includes fixes for proper tick traversal and more precise math.
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

    // 4. Get tick arrays for the swap
    OrcTickArray[] tickArrays = accountFetcher.fetchTickArraysForSwap(pool, aToB);

    // 5. Calculate the swap quote with proper tick traversal
    OrcSwapCalculator.SwapResult swapResult = OrcSwapCalculator.calculateSwapWithTickTraversal(
            amount,
            pool,
            tickArrays,
            aToB,
            swapType == OrcSwapType.EXACT_IN);

    // Convert to a quote
    OrcSwapQuote quote = new OrcSwapQuote();
    if (swapType == OrcSwapType.EXACT_IN) {
      quote.setTokenIn(amount);
      quote.setEstimatedAmountIn(swapResult.getAmountIn());
      quote.setTokenMaxIn(amount);
      quote.setEstimatedAmountOut(swapResult.getAmountOut());

      // Calculate minimum output with slippage
      BigInteger minAmountOut = swapResult.getAmountOut()
              .multiply(BigInteger.valueOf(10000 - slippageToleranceBps))
              .divide(BigInteger.valueOf(10000));

      // Ensure non-zero
      if (minAmountOut.equals(BigInteger.ZERO)) {
        minAmountOut = BigInteger.ONE;
      }

      quote.setTokenMinOut(minAmountOut);
      quote.setFee(swapResult.getFeeAmount());
    } else {
      quote.setTokenIn(swapResult.getAmountIn());
      quote.setEstimatedAmountIn(swapResult.getAmountIn());

      // Calculate maximum input with slippage
      BigInteger maxAmountIn = swapResult.getAmountIn()
              .multiply(BigInteger.valueOf(10000 + slippageToleranceBps))
              .divide(BigInteger.valueOf(10000));

      quote.setTokenMaxIn(maxAmountIn);
      quote.setEstimatedAmountOut(amount);
      quote.setTokenMinOut(amount);
      quote.setFee(swapResult.getFeeAmount());
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

    if (isInputTokenA) {
      log.info("Swapping {} {} for approximately {} {}",
              FixedPointMath.tokenAmountToDecimal(quote.getTokenIn(), decimalsA),
              "Token A",
              FixedPointMath.tokenAmountToDecimal(quote.getEstimatedAmountOut(), decimalsB),
              "Token B");
    } else {
      log.info("Swapping {} {} for approximately {} {}",
              FixedPointMath.tokenAmountToDecimal(quote.getTokenIn(), decimalsB),
              "Token B",
              FixedPointMath.tokenAmountToDecimal(quote.getEstimatedAmountOut(), decimalsA),
              "Token A");
    }

    // 6. Build swap instruction
    byte[] instructionData =
            createSwapInstructionData(
                    amount,
                    swapType == OrcSwapType.EXACT_IN ? quote.getTokenMinOut() : quote.getTokenMaxIn(),
                    BigInteger.ZERO, // No price limit
                    swapType == OrcSwapType.EXACT_IN,
                    aToB);

    // 7. Get oracle address
    String oracleAddress = OrcAddressUtils.getOracleAddress(poolAddress);

    // 8. Build accounts for the instruction
    String[] tickArrayAddresses = new String[tickArrays.length];
    for (int i = 0; i < tickArrays.length; i++) {
      if (tickArrays[i] != null) {
        tickArrayAddresses[i] = tickArrays[i].getAddress();
      } else {
        // Use tick array addresses from the utils if we couldn't fetch them
        tickArrayAddresses = OrcAddressUtils.getTickArrayAddressesForSwap(
                poolAddress, pool.getTickCurrentIndex(), pool.getTickSpacing(), aToB);
        break;
      }
    }

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

  /** Creates the instruction data for a swap. */
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

  /** Builds the account metas required for a swap instruction. */
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
   * Calculates the minimum viable amount for a swap in a given pool.
   * Amounts below this threshold may produce zero output due to precision issues.
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

  /**
   * Builds a swap instruction without executing it.
   * This is used for building atomic transactions.
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

    // 4. Get tick arrays for the swap
    OrcTickArray[] tickArrays = accountFetcher.fetchTickArraysForSwap(pool, aToB);

    // 5. Calculate swap with proper tick traversal
    OrcSwapCalculator.SwapResult swapResult = OrcSwapCalculator.calculateSwapWithTickTraversal(
            amount,
            pool,
            tickArrays,
            aToB,
            swapType == OrcSwapType.EXACT_IN);

    // Convert to quote thresholds
    BigInteger otherAmountThreshold;
    if (swapType == OrcSwapType.EXACT_IN) {
      // Calculate minimum output with slippage
      otherAmountThreshold = swapResult.getAmountOut()
              .multiply(BigInteger.valueOf(10000 - slippageToleranceBps))
              .divide(BigInteger.valueOf(10000));

      // Ensure non-zero
      if (otherAmountThreshold.equals(BigInteger.ZERO)) {
        otherAmountThreshold = BigInteger.ONE;
      }
    } else {
      // Calculate maximum input with slippage
      otherAmountThreshold = swapResult.getAmountIn()
              .multiply(BigInteger.valueOf(10000 + slippageToleranceBps))
              .divide(BigInteger.valueOf(10000));
    }

    // 6. Build swap instruction data
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
    String[] tickArrayAddresses = new String[tickArrays.length];
    for (int i = 0; i < tickArrays.length; i++) {
      if (tickArrays[i] != null) {
        tickArrayAddresses[i] = tickArrays[i].getAddress();
      } else {
        // Use tick array addresses from the utils if we couldn't fetch them
        tickArrayAddresses = OrcAddressUtils.getTickArrayAddressesForSwap(
                poolAddress, pool.getTickCurrentIndex(), pool.getTickSpacing(), aToB);
        break;
      }
    }

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

  /** Creates the instruction data for a swap. */
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

    // sqrt_price_limit (default to 0 = no limit)
    // If aToB, we can set a minimum price limit to prevent excessive slippage
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

  /** Builds the account metas required for a swap instruction. */
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
    accounts.add(AccountMeta.createRead(OrcConstants.TOKEN_PROGRAM_ID)); // token_program_a
    accounts.add(AccountMeta.createRead(OrcConstants.TOKEN_PROGRAM_ID)); // token_program_b
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

  /** Calculates a swap quote for exact input swaps. */
  public OrcSwapQuote calculateExactInSwapQuote(
      BigInteger amountIn, boolean aToB, int slippageToleranceBps, OrcWhirlpool pool) {

    log.debug(
        "Calculating exact-in swap quote - Direction: {}, Input amount: {}",
        aToB ? "A->B" : "B->A",
        amountIn);
    log.debug("Current sqrt price: {}", pool.getSqrtPrice());
    log.debug("Liquidity: {}", pool.getLiquidity());
    log.debug("Fee rate: {}", pool.getFeeRate());

    // Get transfer fees if applicable
    OrcTransferFee transferFeeA = accountFetcher.extractTransferFee(pool.getTokenMintA());
    OrcTransferFee transferFeeB = accountFetcher.extractTransferFee(pool.getTokenMintB());

    // Apply input transfer fee if needed
    BigInteger adjustedAmountIn = amountIn;
    if (aToB && transferFeeA != null) {
      adjustedAmountIn = applyTransferFee(amountIn, transferFeeA);
      log.debug("Applied transfer fee A, adjusted input: {}", adjustedAmountIn);
    } else if (!aToB && transferFeeB != null) {
      adjustedAmountIn = applyTransferFee(amountIn, transferFeeB);
      log.debug("Applied transfer fee B, adjusted input: {}", adjustedAmountIn);
    }

    // Apply fee rate to input amount
    BigInteger amountInAfterFee = OrcMathUtils.applyFeeRate(adjustedAmountIn, pool.getFeeRate());
    log.debug("Amount after fee: {}", amountInAfterFee);

    // Calculate next sqrt price
    BigInteger currentSqrtPrice = pool.getSqrtPrice();
    BigInteger nextSqrtPrice;
    BigInteger amountOut;

    try {
      if (aToB) {
        // A to B swap (price goes down)
        nextSqrtPrice =
            OrcMathUtils.getNextSqrtPriceFromTokenAInput(
                currentSqrtPrice, pool.getLiquidity(), amountInAfterFee, true);
        log.debug("A->B swap, next sqrt price: {}", nextSqrtPrice);

        // Ensure the price doesn't go below minimum
        if (nextSqrtPrice.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
          log.debug("Clamping next sqrt price to MIN_SQRT_PRICE: {}", OrcConstants.MIN_SQRT_PRICE);
          nextSqrtPrice = OrcConstants.MIN_SQRT_PRICE;
        }

        amountOut =
            OrcMathUtils.getTokenBDelta(pool.getLiquidity(), nextSqrtPrice, currentSqrtPrice);
      } else {
        // B to A swap (price goes up)
        nextSqrtPrice =
            OrcMathUtils.getNextSqrtPriceFromTokenBInput(
                currentSqrtPrice, pool.getLiquidity(), amountInAfterFee, true);
        log.debug("B->A swap, next sqrt price: {}", nextSqrtPrice);

        // Ensure the price doesn't go above maximum
        if (nextSqrtPrice.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
          log.debug("Clamping next sqrt price to MAX_SQRT_PRICE: {}", OrcConstants.MAX_SQRT_PRICE);
          nextSqrtPrice = OrcConstants.MAX_SQRT_PRICE;
        }

        amountOut =
            OrcMathUtils.getTokenADelta(pool.getLiquidity(), currentSqrtPrice, nextSqrtPrice);
      }
    } catch (ArithmeticException e) {
      log.error("Arithmetic error in swap calculation: {}", e.getMessage());
      throw new RuntimeException("Error calculating swap amounts: " + e.getMessage());
    }

    log.debug("Calculated raw output amount: {}", amountOut);

    // Check for zero output - this will cause program error 101
    if (amountOut.equals(BigInteger.ZERO)) {
      log.warn("Calculated output amount is zero, setting to minimum non-zero amount");
      amountOut = BigInteger.ONE;
    }

    // Apply output transfer fee if needed
    if (aToB && transferFeeB != null) {
      amountOut = applyTransferFee(amountOut, transferFeeB);
      log.debug("Applied output transfer fee, adjusted output: {}", amountOut);
    } else if (!aToB && transferFeeA != null) {
      amountOut = applyTransferFee(amountOut, transferFeeA);
      log.debug("Applied output transfer fee, adjusted output: {}", amountOut);
    }

    // Calculate minimum output with slippage
    BigInteger minAmountOut =
        amountOut
            .multiply(BigInteger.valueOf(10000 - slippageToleranceBps))
            .divide(BigInteger.valueOf(10000));

    // Ensure minimum output is non-zero
    if (minAmountOut.equals(BigInteger.ZERO)) {
      log.debug("Minimum output after slippage is zero, setting to 1");
      minAmountOut = BigInteger.ONE;
    }

    log.debug("Final quote - estimatedOut: {}, minOut: {}", amountOut, minAmountOut);

    return OrcSwapQuote.builder()
        .tokenIn(amountIn)
        .estimatedAmountIn(amountIn)
        .tokenMaxIn(amountIn)
        .estimatedAmountOut(amountOut)
        .tokenMinOut(minAmountOut)
        .build();
  }

  /** Calculates a swap quote for exact output swaps. */
  OrcSwapQuote calculateExactOutSwapQuote(
      BigInteger amountOut, boolean aToB, int slippageToleranceBps, OrcWhirlpool pool) {

    log.debug(
        "Calculating exact-out swap quote - Direction: {}, Output amount: {}",
        aToB ? "A->B" : "B->A",
        amountOut);

    // Check for zero output
    if (amountOut.equals(BigInteger.ZERO)) {
      throw new IllegalArgumentException("Output amount cannot be zero");
    }

    // Get transfer fees if applicable
    OrcTransferFee transferFeeA = accountFetcher.extractTransferFee(pool.getTokenMintA());
    OrcTransferFee transferFeeB = accountFetcher.extractTransferFee(pool.getTokenMintB());

    // Apply output transfer fee if needed (reverse calculation)
    BigInteger adjustedAmountOut = amountOut;
    if (aToB && transferFeeB != null) {
      // Need to calculate what output would generate this amount after fee
      adjustedAmountOut = reverseApplyTransferFee(amountOut, transferFeeB);
      log.debug("Reversed transfer fee B, adjusted output: {}", adjustedAmountOut);
    } else if (!aToB && transferFeeA != null) {
      adjustedAmountOut = reverseApplyTransferFee(amountOut, transferFeeA);
      log.debug("Reversed transfer fee A, adjusted output: {}", adjustedAmountOut);
    }

    // Calculate required input amount
    BigInteger currentSqrtPrice = pool.getSqrtPrice();
    BigInteger targetSqrtPrice;
    BigInteger amountIn;

    try {
      if (aToB) {
        // Find target sqrt price to get exactly adjustedAmountOut of token B
        // This is a simplified approach; full implementation would traverse tick arrays
        BigInteger deltaPrice = adjustedAmountOut.shiftLeft(64).divide(pool.getLiquidity());
        targetSqrtPrice = currentSqrtPrice.subtract(deltaPrice);

        // Ensure target price is within bounds
        if (targetSqrtPrice.compareTo(OrcConstants.MIN_SQRT_PRICE) < 0) {
          targetSqrtPrice = OrcConstants.MIN_SQRT_PRICE;
        }

        // Calculate token A needed
        amountIn =
            OrcMathUtils.getTokenADelta(pool.getLiquidity(), targetSqrtPrice, currentSqrtPrice);
      } else {
        // Find target sqrt price to get exactly adjustedAmountOut of token A
        BigInteger numerator =
            adjustedAmountOut.multiply(currentSqrtPrice).multiply(currentSqrtPrice);
        BigInteger denominator = pool.getLiquidity().shiftLeft(64);
        targetSqrtPrice = currentSqrtPrice.add(numerator.divide(denominator));

        // Ensure target price is within bounds
        if (targetSqrtPrice.compareTo(OrcConstants.MAX_SQRT_PRICE) > 0) {
          targetSqrtPrice = OrcConstants.MAX_SQRT_PRICE;
        }

        // Calculate token B needed
        amountIn =
            OrcMathUtils.getTokenBDelta(pool.getLiquidity(), currentSqrtPrice, targetSqrtPrice);
      }
    } catch (ArithmeticException e) {
      log.error("Arithmetic error in exact-out swap calculation: {}", e.getMessage());
      throw new RuntimeException("Error calculating exact-out swap: " + e.getMessage());
    }

    log.debug("Calculated raw input amount: {}", amountIn);

    // Check for zero input
    if (amountIn.equals(BigInteger.ZERO)) {
      log.warn("Calculated input amount is zero, setting to minimum non-zero amount");
      amountIn = BigInteger.ONE;
    }

    // Apply fee rate to get gross input amount
    BigInteger amountInBeforeFee =
        amountIn
            .multiply(BigInteger.valueOf(1_000_000))
            .divide(BigInteger.valueOf(1_000_000 - pool.getFeeRate()));

    log.debug("Amount before fee: {}", amountInBeforeFee);

    // Apply input transfer fee if needed
    if (aToB && transferFeeA != null) {
      amountInBeforeFee = reverseApplyTransferFee(amountInBeforeFee, transferFeeA);
      log.debug("Reversed input transfer fee, final input: {}", amountInBeforeFee);
    } else if (!aToB && transferFeeB != null) {
      amountInBeforeFee = reverseApplyTransferFee(amountInBeforeFee, transferFeeB);
      log.debug("Reversed input transfer fee, final input: {}", amountInBeforeFee);
    }

    // Calculate maximum input with slippage
    BigInteger maxAmountIn =
        amountInBeforeFee
            .multiply(BigInteger.valueOf(10000 + slippageToleranceBps))
            .divide(BigInteger.valueOf(10000));

    log.debug(
        "Final quote - estimatedIn: {}, maxIn: {}, out: {}",
        amountInBeforeFee,
        maxAmountIn,
        amountOut);

    return OrcSwapQuote.builder()
        .tokenIn(amountInBeforeFee)
        .estimatedAmountIn(amountInBeforeFee)
        .tokenMaxIn(maxAmountIn)
        .estimatedAmountOut(amountOut)
        .tokenMinOut(amountOut)
        .build();
  }

  /** Applies a transfer fee to an amount. */
  private BigInteger applyTransferFee(BigInteger amount, OrcTransferFee transferFee) {
    if (transferFee == null) {
      return amount;
    }

    BigInteger feeAmount =
        amount
            .multiply(BigInteger.valueOf(transferFee.getFeeBasisPoints()))
            .divide(BigInteger.valueOf(10000));

    if (transferFee.getMaxFee() != null && feeAmount.compareTo(transferFee.getMaxFee()) > 0) {
      feeAmount = transferFee.getMaxFee();
    }

    return amount.subtract(feeAmount);
  }

  /** Calculates the pre-fee amount given a post-fee amount. */
  private BigInteger reverseApplyTransferFee(BigInteger postFeeAmount, OrcTransferFee transferFee) {
    if (transferFee == null) {
      return postFeeAmount;
    }

    // For simplicity, this is an approximation
    BigInteger preFeeAmount =
        postFeeAmount
            .multiply(BigInteger.valueOf(10000))
            .divide(BigInteger.valueOf(10000 - transferFee.getFeeBasisPoints()));

    return preFeeAmount;
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

  /**
   * Builds a swap instruction without executing it. This is used for building atomic transactions.
   *
   * @param poolAddress The address of the pool
   * @param amount The amount to swap
   * @param inputMint The input token mint
   * @param swapType The type of swap
   * @param slippageToleranceBps Slippage tolerance in basis points
   * @param walletAddress The wallet address
   * @param signer The transaction signer
   * @return The swap instruction
   * @throws Exception If there's an error building the instruction
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

    // 4. Get tick arrays for the swap
    String[] tickArrayAddresses =
        OrcAddressUtils.getTickArrayAddressesForSwap(
            poolAddress, pool.getTickCurrentIndex(), pool.getTickSpacing(), aToB);

    // 5. Calculate the swap quote
    OrcSwapQuote quote;
    if (swapType == OrcSwapType.EXACT_IN) {
      quote = calculateExactInSwapQuote(amount, aToB, slippageToleranceBps, pool);
    } else {
      quote = calculateExactOutSwapQuote(amount, aToB, slippageToleranceBps, pool);
    }

    // 6. Build swap instruction data
    byte[] instructionData =
        createSwapInstructionData(
            amount,
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

    // 9. Create and return instruction
    return Instruction.createInstruction(
        OrcConstants.WHIRLPOOL_PROGRAM_ID, accounts, instructionData);
  }
}
