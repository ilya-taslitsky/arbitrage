package com.crypto.arbitrage.providers.orc.services;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.*;
import com.crypto.arbitrage.providers.orc.utils.OrcAddressUtils;
import com.crypto.arbitrage.providers.orc.utils.OrcMathUtils;
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

/** Service for executing swaps on Orca Whirlpools. */
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
  OrcSwapQuote calculateExactInSwapQuote(
      BigInteger amountIn, boolean aToB, int slippageToleranceBps, OrcWhirlpool pool) {

    // Get transfer fees if applicable
    OrcTransferFee transferFeeA = accountFetcher.extractTransferFee(pool.getTokenMintA());
    OrcTransferFee transferFeeB = accountFetcher.extractTransferFee(pool.getTokenMintB());

    // Apply input transfer fee if needed
    BigInteger adjustedAmountIn = amountIn;
    if (aToB && transferFeeA != null) {
      adjustedAmountIn = applyTransferFee(amountIn, transferFeeA);
    } else if (!aToB && transferFeeB != null) {
      adjustedAmountIn = applyTransferFee(amountIn, transferFeeB);
    }

    // Apply fee rate to input amount
    BigInteger amountInAfterFee = OrcMathUtils.applyFeeRate(adjustedAmountIn, pool.getFeeRate());

    // Calculate next sqrt price
    BigInteger currentSqrtPrice = pool.getSqrtPrice();
    BigInteger nextSqrtPrice;
    BigInteger amountOut;

    if (aToB) {
      // A to B swap (price goes down)
      nextSqrtPrice =
          OrcMathUtils.getNextSqrtPriceFromTokenAInput(
              currentSqrtPrice, pool.getLiquidity(), amountInAfterFee, true);

      amountOut = OrcMathUtils.getTokenBDelta(pool.getLiquidity(), nextSqrtPrice, currentSqrtPrice);
    } else {
      // B to A swap (price goes up)
      nextSqrtPrice =
          OrcMathUtils.getNextSqrtPriceFromTokenBInput(
              currentSqrtPrice, pool.getLiquidity(), amountInAfterFee, true);

      amountOut = OrcMathUtils.getTokenADelta(pool.getLiquidity(), currentSqrtPrice, nextSqrtPrice);
    }

    // Apply output transfer fee if needed
    if (aToB && transferFeeB != null) {
      amountOut = applyTransferFee(amountOut, transferFeeB);
    } else if (!aToB && transferFeeA != null) {
      amountOut = applyTransferFee(amountOut, transferFeeA);
    }

    // Calculate minimum output with slippage
    BigInteger minAmountOut =
        amountOut
            .multiply(BigInteger.valueOf(10000 - slippageToleranceBps))
            .divide(BigInteger.valueOf(10000));

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

    // Get transfer fees if applicable
    OrcTransferFee transferFeeA = accountFetcher.extractTransferFee(pool.getTokenMintA());
    OrcTransferFee transferFeeB = accountFetcher.extractTransferFee(pool.getTokenMintB());

    // Apply output transfer fee if needed (reverse calculation)
    BigInteger adjustedAmountOut = amountOut;
    if (aToB && transferFeeB != null) {
      // Need to calculate what output would generate this amount after fee
      adjustedAmountOut = reverseApplyTransferFee(amountOut, transferFeeB);
    } else if (!aToB && transferFeeA != null) {
      adjustedAmountOut = reverseApplyTransferFee(amountOut, transferFeeA);
    }

    // Calculate required input amount
    BigInteger currentSqrtPrice = pool.getSqrtPrice();
    BigInteger targetSqrtPrice;
    BigInteger amountIn;

    if (aToB) {
      // Find target sqrt price to get exactly adjustedAmountOut of token B
      // This is a simplified approach; full implementation would traverse tick arrays
      BigInteger deltaPrice = adjustedAmountOut.shiftLeft(64).divide(pool.getLiquidity());
      targetSqrtPrice = currentSqrtPrice.subtract(deltaPrice);

      // Calculate token A needed
      amountIn =
          OrcMathUtils.getTokenADelta(pool.getLiquidity(), targetSqrtPrice, currentSqrtPrice);
    } else {
      // Find target sqrt price to get exactly adjustedAmountOut of token A
      BigInteger numerator =
          adjustedAmountOut.multiply(currentSqrtPrice).multiply(currentSqrtPrice);
      BigInteger denominator = pool.getLiquidity().shiftLeft(64);
      targetSqrtPrice = currentSqrtPrice.add(numerator.divide(denominator));

      // Calculate token B needed
      amountIn =
          OrcMathUtils.getTokenBDelta(pool.getLiquidity(), currentSqrtPrice, targetSqrtPrice);
    }

    // Apply fee rate to get gross input amount
    BigInteger amountInBeforeFee =
        amountIn
            .multiply(BigInteger.valueOf(1_000_000))
            .divide(BigInteger.valueOf(1_000_000 - pool.getFeeRate()));

    // Apply input transfer fee if needed
    if (aToB && transferFeeA != null) {
      amountInBeforeFee = reverseApplyTransferFee(amountInBeforeFee, transferFeeA);
    } else if (!aToB && transferFeeB != null) {
      amountInBeforeFee = reverseApplyTransferFee(amountInBeforeFee, transferFeeB);
    }

    // Calculate maximum input with slippage
    BigInteger maxAmountIn =
        amountInBeforeFee
            .multiply(BigInteger.valueOf(10000 + slippageToleranceBps))
            .divide(BigInteger.valueOf(10000));

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
