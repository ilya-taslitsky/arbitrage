package com.crypto.arbitrage.providers.orc;

import com.crypto.arbitrage.providers.orc.model.OrcAllPoolsInfoResp;
import com.crypto.arbitrage.providers.orc.model.OrcPoolInfoResp;
import com.crypto.arbitrage.providers.orc.model.transaction.OrcSwapParams;
import com.crypto.arbitrage.providers.orc.model.transaction.OrcSwapTransactionResult;
import com.crypto.arbitrage.providers.orc.service.OrcSwapTransactionService;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import software.sava.core.accounts.Signer;
import software.sava.core.encoding.Base58;
import software.sava.rpc.json.http.client.SolanaRpcClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrcProvider {

  private static final String ORCA_WHIRLPOOL_PROGRAM_ID =
          "whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc";
  private static final String SOL_USDC_POOL_ADDRESS =
          "Czfq3xZZDmsdGdUyrNLtRhGc47cXcZtLG4crryfu44zE";
  private static final String WSOL_MINT =
          "So11111111111111111111111111111111111111112";
  private static final String USDC_MINT =
          "EPjFWdd5AufqSSqeM2q8E3ft6Gbv3ZYwfyAbvZz49Hdj";
  private static final String MY_WALLET_ADDRESS =
          "BYcFJHa3pb5WLvr3rbu8KsFXeJgGeuxnhRRXKWfzz887";
  private static final String MY_WALLET_PRIVATE_KEY =
          "4BTSmN1jnmsMDtvFSjzfTK2pkhexAXcQuWxeKQe83LXvDKtb8KjnY3PE9sUBhqzpS2vUyKCtkLAsQvsPcJpAnWrj";
  private static final int SLIPPAGE_TOLERANCE = 100;

  private final RestClient restClient;
  private final SolanaRpcClient rpcClient;
  private final OrcSwapTransactionService swapTransactionService;

  public void getAccountInfoViaSolana(String publicKey) {
    var accountInfo = rpcClient
            .getAccountInfo(software.sava.core.accounts.PublicKey.fromBase58Encoded(publicKey), null)
            .join();
    log.info(accountInfo.toString());
  }

  public OrcAllPoolsInfoResp getAllPoolsInfoViaOrcApi() {
    try {
      OrcAllPoolsInfoResp resp =
              restClient.get()
                      .uri("https://api.orca.so/v2/solana/pools/all")
                      .header("Content-Type", "application/json")
                      .retrieve()
                      .body(OrcAllPoolsInfoResp.class);
      log.info(resp != null ? resp.toString() : "Response is null");
      return resp;
    } catch (Exception e) {
      log.error(e.getMessage());
    }
    return null;
  }

  public OrcPoolInfoResp getPoolInfoViaOrcApi(String publicKey) {
    try {
      OrcPoolInfoResp resp =
              restClient.get()
                      .uri("https://api.orca.so/v2/solana/pools/" + publicKey)
                      .header("Content-Type", "application/json")
                      .retrieve()
                      .body(OrcPoolInfoResp.class);
      log.info(resp != null ? resp.toString() : "Response is null");
      return resp;
    } catch (Exception e) {
      log.error(e.getMessage());
    }
    return null;
  }

  /**
   * Executes (simulates) a swap transaction.
   *
   * <p>This method creates swap parameters (an exact–input swap, with outputAmount set to null),
   * creates a Signer from the wallet private key, and then delegates the swap execution
   * to our OrcSwapTransactionService.
   *
   * @param inputAmount The input token amount.
   * @return The OrcSwapTransactionResult containing the swap instructions and the estimated output.
   * @throws Exception If any error occurs.
   */
  public OrcSwapTransactionResult executeSwapTransaction(long inputAmount) throws Exception {
    // For an exact-input swap, leave outputAmount null.
    OrcSwapParams params = new OrcSwapParams();
    params.setMint(WSOL_MINT); // assuming swapping WSOL (token A) for USDC (token B)

    // Create Signer from the wallet private key.
    Signer signer = Signer.createFromPrivateKey(Base58.decode(MY_WALLET_PRIVATE_KEY));

    // Delegate to our swap transaction service.
    return swapTransactionService.executeSwapTransaction(
            BigInteger.valueOf(inputAmount),
            params,
            SOL_USDC_POOL_ADDRESS,
            SLIPPAGE_TOLERANCE,
            signer
    );
  }
}
