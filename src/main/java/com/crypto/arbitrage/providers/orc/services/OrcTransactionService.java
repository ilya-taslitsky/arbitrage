package com.crypto.arbitrage.providers.orc.services;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.sava.core.accounts.Signer;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.TxSimulation;

/** Service for handling Solana transaction operations. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrcTransactionService {

  private final SolanaRpcClient rpcClient;

  /**
   * Simulates a transaction on the Solana network.
   *
   * @param instructions The list of instructions to simulate
   * @param signer The transaction signer
   * @return A CompletableFuture that resolves to the simulation result
   */
  public CompletableFuture<TxSimulation> simulateTransaction(
      List<Instruction> instructions, Signer signer) {
    try {
      log.info("Simulating transaction with {} instructions", instructions.size());

      // Get latest blockhash
      var blockHashFuture = rpcClient.getLatestBlockHash();

      // Create transaction
      var transaction = Transaction.createTx(signer.publicKey(), instructions);

      // Set blockhash and sign transaction
      var blockHash = blockHashFuture.join().blockHash();
      transaction.setRecentBlockHash(blockHash);
      transaction.sign(signer);

      // Encode transaction for simulation
      var base64EncodedTx = transaction.base64EncodeToString();

      // Simulate transaction
      return rpcClient.simulateTransaction(base64EncodedTx);
    } catch (Exception e) {
      log.error("Error simulating transaction: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to simulate transaction", e);
    }
  }

  /**
   * Sends a transaction to the Solana network.
   *
   * @param instructions The list of instructions to execute
   * @param signer The transaction signer
   * @return The transaction signature
   */
  public String sendTransaction(List<Instruction> instructions, Signer signer) {
    return sendTransactionWithSigners(instructions, Arrays.asList(signer));
  }

  /**
   * Sends a transaction to the Solana network with multiple signers.
   *
   * @param instructions The list of instructions to execute
   * @param signers The list of transaction signers
   * @return The transaction signature
   */
  public String sendTransactionWithSigners(List<Instruction> instructions, List<Signer> signers) {
    try {
      if (signers.isEmpty()) {
        throw new IllegalArgumentException("At least one signer is required");
      }

      log.info(
          "Sending transaction with {} instructions and {} signers",
          instructions.size(),
          signers.size());

      // Use the first signer as the fee payer
      Signer feePayer = signers.get(0);

      // Simulate first to catch errors
      TxSimulation sim = simulateTransaction(instructions, feePayer).join();
      if (sim.error() != null) {
        log.error("Transaction simulation failed: {}", sim.error());
        throw new RuntimeException("Transaction simulation failed: " + sim.error());
      }

      // Get latest blockhash
      var blockHashFuture = rpcClient.getLatestBlockHash();

      // Create transaction
      var transaction = Transaction.createTx(feePayer.publicKey(), instructions);

      // Set blockhash
      var blockHash = blockHashFuture.join().blockHash();
      transaction.setRecentBlockHash(blockHash);

      // Sign with all signers
      for (Signer signer : signers) {
        transaction.sign(signer);
      }

      // Encode and send transaction
      var base64EncodedTx = transaction.base64EncodeToString();
      String signature = rpcClient.sendTransaction(base64EncodedTx).join();

      log.info("Transaction sent with signature: {}", signature);
      return signature;
    } catch (Exception e) {
      log.error("Error sending transaction: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to send transaction", e);
    }
  }
}
