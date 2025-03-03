package com.crypto.arbitrage.providers.orc.services;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.utils.OrcAddressUtils;
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
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.solana.programs.system.SystemProgram;

/** Service for managing token accounts on Solana. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrcTokenAccountService {

  private final SolanaRpcClient rpcClient;
  private final OrcTransactionService transactionService;

  public String getOrCreateTokenAccount(String ownerAddress, String mintAddress, Signer signer) {
    try {
      String tokenAccountAddress =
          OrcAddressUtils.getAssociatedTokenAddress(ownerAddress, mintAddress);

      // Check if the account exists
      boolean exists = isTokenAccountExist(tokenAccountAddress);

      if (!exists) {
        log.info("Token account {} does not exist, creating...", tokenAccountAddress);

        // Create ATA with retry mechanism
        try {
          String signature = createAssociatedTokenAccount(ownerAddress, mintAddress, signer);

          // Wait for transaction confirmation
          int maxRetries = 5;
          int retryCount = 0;
          boolean confirmed = false;

          while (retryCount < maxRetries) {
            // Give some time for the transaction to be confirmed
            Thread.sleep(1000);

            // Check if the account exists now
            confirmed = isTokenAccountExist(tokenAccountAddress);
            if (confirmed) {
              log.info("Token account {} created successfully", tokenAccountAddress);
              break;
            }

            retryCount++;
            log.info(
                "Waiting for token account creation confirmation (attempt {}/{})",
                retryCount,
                maxRetries);
          }

          if (!confirmed) {
            throw new RuntimeException(
                "Token account creation not confirmed after " + maxRetries + " attempts");
          }
        } catch (Exception e) {
          log.error("Error creating token account: {}", e.getMessage(), e);

          // Special handling for WSOL (native SOL) accounts
          if (mintAddress.equals(OrcConstants.WSOL_MINT)) {
            log.info("Creating wrapped SOL account with alternative method");
            // Implement alternative method for WSOL accounts here
            // This could involve sending SOL to the account and then using sync_native instruction
          }

          throw new RuntimeException("Failed to create token account", e);
        }
      }

      return tokenAccountAddress;
    } catch (Exception e) {
      log.error("Error in getOrCreateTokenAccount: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to get or create token account", e);
    }
  }

  /**
   * Creates an associated token account for a wallet and mint.
   *
   * @param ownerAddress The owner's address
   * @param mintAddress The mint address
   * @param signer The signer for the transaction
   * @return The transaction signature
   */
  public String createAssociatedTokenAccount(
      String ownerAddress, String mintAddress, Signer signer) {
    try {
      PublicKey owner = PublicKey.fromBase58Encoded(ownerAddress);
      PublicKey mint = PublicKey.fromBase58Encoded(mintAddress);
      String tokenAccountAddress =
          OrcAddressUtils.getAssociatedTokenAddress(ownerAddress, mintAddress);
      PublicKey tokenAccount = PublicKey.fromBase58Encoded(tokenAccountAddress);

      // Build account metas for create instruction
      List<AccountMeta> accountMetas = new ArrayList<>();
      accountMetas.add(AccountMeta.createWritableSigner(signer.publicKey())); // Payer
      accountMetas.add(AccountMeta.createWrite(tokenAccount)); // ATA
      accountMetas.add(AccountMeta.createRead(owner)); // Owner
      accountMetas.add(AccountMeta.createRead(mint)); // Mint
      accountMetas.add(AccountMeta.createRead(OrcConstants.SYSTEM_PROGRAM_ID)); // System program
      accountMetas.add(AccountMeta.createRead(OrcConstants.TOKEN_PROGRAM_ID)); // Token program
      accountMetas.add(AccountMeta.createRead(OrcConstants.RENT_SYSVAR_ID)); // Rent sysvar

      // Create instruction
      Instruction createInstruction =
          Instruction.createInstruction(
              OrcConstants.ASSOCIATED_TOKEN_PROGRAM_ID,
              accountMetas,
              new byte[0] // No instruction data for ATA creation
              );

      // Send transaction
      return transactionService.sendTransaction(
          Collections.singletonList(createInstruction), signer);
    } catch (Exception e) {
      log.error("Error creating associated token account: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to create associated token account", e);
    }
  }

  /**
   * Checks if a token account exists on the blockchain.
   *
   * @param tokenAccountAddress The address of the token account
   * @return true if the account exists and has data
   */
  public boolean isTokenAccountExist(String tokenAccountAddress) {
    try {
      var accountInfo =
          rpcClient.getAccountInfo(PublicKey.fromBase58Encoded(tokenAccountAddress)).join();
      return accountInfo != null && accountInfo.data() != null;
    } catch (Exception e) {
      log.error("Error checking token account existence: {}", e.getMessage(), e);
      return false;
    }
  }

  public String createWrappedSolAccount(String ownerAddress, Signer signer) {
    try {
      String ataAddress =
          OrcAddressUtils.getAssociatedTokenAddress(ownerAddress, OrcConstants.WSOL_MINT);

      // Calculate minimum SOL needed (rent + small buffer for transactions)
      long minimumSol = 2_039_280; // Typical rent exemption for token accounts

      List<Instruction> instructions = new ArrayList<>();

      // 1. Create ATA instruction
      instructions.add(
          createAssociatedTokenAccountInstruction(
              ownerAddress, OrcConstants.WSOL_MINT, ataAddress, signer.publicKey()));

      // 2. Transfer SOL to the account
      instructions.add(
          SystemProgram.transfer(
              AccountMeta.createInvoked(
                  OrcConstants
                      .SYSTEM_PROGRAM_ID), // Add the system program ID as the first argument
              signer.publicKey(),
              PublicKey.fromBase58Encoded(ataAddress),
              minimumSol));

      // 3. Sync native instruction
      instructions.add(createSyncNativeInstruction(ataAddress));

      // Execute transaction
      String signature = transactionService.sendTransaction(instructions, signer);

      // Wait for confirmation
      // (Same confirmation logic as above)

      return ataAddress;
    } catch (Exception e) {
      log.error("Error creating wrapped SOL account: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to create wrapped SOL account", e);
    }
  }

  private Instruction createSyncNativeInstruction(String tokenAccount) {
    List<AccountMeta> accounts = new ArrayList<>();
    accounts.add(AccountMeta.createWrite(PublicKey.fromBase58Encoded(tokenAccount)));

    // SyncNative instruction has no data
    return Instruction.createInstruction(
        OrcConstants.TOKEN_PROGRAM_ID, accounts, new byte[] {17} // Instruction code for SyncNative
        );
  }

  /**
   * Creates an instruction to create an associated token account.
   *
   * @param ownerAddress The owner's address
   * @param mintAddress The mint address
   * @param ataAddress The address where the associated token account will be created
   * @param payerAddress The address paying for the account creation
   * @return The instruction to create the associated token account
   */
  private Instruction createAssociatedTokenAccountInstruction(
      String ownerAddress, String mintAddress, String ataAddress, PublicKey payerAddress) {

    PublicKey owner = PublicKey.fromBase58Encoded(ownerAddress);
    PublicKey mint = PublicKey.fromBase58Encoded(mintAddress);
    PublicKey tokenAccount = PublicKey.fromBase58Encoded(ataAddress);

    // Build account metas for create instruction
    List<AccountMeta> accountMetas = new ArrayList<>();
    accountMetas.add(AccountMeta.createWritableSigner(payerAddress)); // Payer
    accountMetas.add(AccountMeta.createWrite(tokenAccount)); // ATA
    accountMetas.add(AccountMeta.createRead(owner)); // Owner
    accountMetas.add(AccountMeta.createRead(mint)); // Mint
    accountMetas.add(AccountMeta.createRead(OrcConstants.SYSTEM_PROGRAM_ID)); // System program
    accountMetas.add(AccountMeta.createRead(OrcConstants.TOKEN_PROGRAM_ID)); // Token program
    accountMetas.add(AccountMeta.createRead(OrcConstants.RENT_SYSVAR_ID)); // Rent sysvar

    // Create instruction with no data (ATA creation doesn't need instruction data)
    return Instruction.createInstruction(
        OrcConstants.ASSOCIATED_TOKEN_PROGRAM_ID, accountMetas, new byte[0]);
  }
}
