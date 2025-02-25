package com.crypto.arbitrage.providers.orc.service;

import com.crypto.arbitrage.providers.orc.model.transaction.OrcSwapParams;
import com.crypto.arbitrage.providers.orc.model.transaction.OrcSwapQuote;
import com.crypto.arbitrage.providers.orc.model.transaction.OrcSwapTransactionResult;
import com.crypto.arbitrage.providers.orc.model.transaction.OrcTickArray;
import com.crypto.arbitrage.providers.orc.model.transaction.OrcWhirlpool;
import com.crypto.arbitrage.providers.orc.util.OrcSwapInstructionBuilder;
import com.crypto.arbitrage.providers.orc.util.OrcSwapQuoteCalculator;
import com.crypto.arbitrage.providers.orc.util.OrcTickArrayFetcher;
import com.crypto.arbitrage.providers.orc.util.OrcWhirlpoolFetcher;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.sava.core.accounts.Signer;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.client.SolanaRpcClient;

@Service
@RequiredArgsConstructor
public class OrcSwapTransactionService {

  private final SolanaRpcClient rpcClient;
  private final OrcWhirlpoolFetcher whirlpoolFetcher;
  private final OrcSwapQuoteCalculator swapQuoteCalculator;
  private final OrcTickArrayFetcher tickArrayFetcher;
  private final OrcSwapInstructionBuilder instructionBuilder;

  /**
   * Executes a swap transaction using the real Orca Whirlpool SDK logic.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Fetches the pool state from the blockchain.
   *   <li>Retrieves the tick arrays needed for swap calculations.
   *   <li>Computes the swap quote using either exact–input or exact–output logic.
   *   <li>Builds the swap instruction (using the tick arrays and pool vault accounts).
   *   <li>Returns the assembled instructions and the estimated output amount.
   * </ol>
   *
   * @param inputAmount The input token amount (if exact–input) as a BigInteger.
   * @param params The swap parameters.
   * @param poolAddress The address of the Whirlpool pool.
   * @param slippageToleranceBps The slippage tolerance in basis points.
   * @param signer The wallet signer executing the swap.
   * @return A result object containing the swap instructions and estimated token output.
   * @throws Exception If any RPC or processing error occurs.
   */
  public OrcSwapTransactionResult executeSwapTransaction(
      BigInteger inputAmount,
      OrcSwapParams params,
      String poolAddress,
      int slippageToleranceBps,
      Signer signer)
      throws Exception {

    // 1. Fetch pool state from the blockchain.
    OrcWhirlpool pool = whirlpoolFetcher.fetchWhirlpool(poolAddress);

    // 2. Fetch tick arrays required for swap calculations.
    OrcTickArray[] tickArrays = tickArrayFetcher.fetchTickArrays(rpcClient, pool);

    // 3. Compute the swap quote based on whether we have an exact–input or exact–output swap.
    OrcSwapQuote quote;
    if (params.isExactInput()) {
      quote =
          swapQuoteCalculator.calculateQuoteByInput(
              inputAmount,
              params.isTokenAMint(),
              slippageToleranceBps,
              pool,
              tickArrays,
              null, // transferFeeA (if applicable)
              null // transferFeeB (if applicable)
              );
    } else {
      quote =
          swapQuoteCalculator.calculateQuoteByOutput(
              params.getOutputAmount(),
              params.isTokenAMint(),
              slippageToleranceBps,
              pool,
              tickArrays,
              null,
              null);
    }

    // 4. Build swap instruction data.
    byte[] swapData = instructionBuilder.createSwapInstructionData(inputAmount, quote.tokenMinOut);

    // 5. Build the account metas required by the swap instruction.
    List<AccountMeta> accounts =
        instructionBuilder.buildSwapAccounts(
            pool,
            signer.publicKey().toBase58(),
            params.getUserTokenAccountA(),
            params.getUserTokenAccountB(),
            pool.tokenVaultA,
            pool.tokenVaultB,
            tickArrays[0].address,
            tickArrays[1].address,
            tickArrays[2].address,
            tickArrays[3].address,
            tickArrays[4].address);

    // 6. Build the swap instruction.
    Instruction swapInstruction = instructionBuilder.buildSwapInstruction(swapData, accounts);
    List<Instruction> instructions = Collections.singletonList(swapInstruction);

    // 7. Return the transaction result with the estimated output (using the field
    // "estimatedAmountOut")
    return new OrcSwapTransactionResult(instructions, null, quote.estimatedAmountOut.longValue());
  }
}
