package com.crypto.arbitrage.providers.orc;

import com.crypto.arbitrage.providers.orc.model.OrcAllPoolsInfoResp;
import com.crypto.arbitrage.providers.orc.model.OrcPoolInfoResp;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.Signer;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.TxSimulation;
import software.sava.solana.programs.clients.NativeProgramAccountClient;
import software.sava.solana.programs.clients.NativeProgramClient;
import software.sava.solana.programs.compute_budget.ComputeBudgetProgram;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrcProvider {

  // Программа Orca Whirlpool
  private static final String ORCA_WHIRLPOOL_PROGRAM_ID =
      "whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc";

  // Адрес пула SOL–USDC (mainnet)
  private static final String SOL_USDC_POOL_ADDRESS =
      "Czfq3xZZDmsdGdUyrNLtRhGc47cXcZtLG4crryfu44zE";

  // Адрес wSOL (wrapped SOL) mint на mainnet
  // (используется, если вы меняете SOL -> USDC)
  private static final String WSOL_MINT = "So11111111111111111111111111111111111111112";

  // Адрес USDC mint на mainnet
  private static final String USDC_MINT = "EPjFWdd5AufqSSqeM2q8E3ft6Gbv3ZYwfyAbvZz49Hdj";

  // Ваш кошелёк
  private static final String MY_WALLET_ADDRESS = "BYcFJHa3pb5WLvr3rbu8KsFXeJgGeuxnhRRXKWfzz887";
  private static final String MY_WALLET_PRIVATE_KEY =
      "4BTSmN1jnmsMDtvFSjzfTK2pkhexAXcQuWxeKQe83LXvDKtb8KjnY3PE9sUBhqzpS2vUyKCtkLAsQvsPcJpAnWrj";

  private static final String GET_POOL = "/pools/";
  private static final String GET_ALL_POOLS_INFO_URL = "/pools/all";
  private static final String ORC_API_URL = "https://api.orca.so/v2/solana";

  private final RestClient restClient;
  private final SolanaRpcClient rpcClient;

  public void getAccountInfoViaSolana(@NonNull String publicKey) {
    var accountInfo =
        rpcClient
            .getAccountInfo(PublicKey.fromBase58Encoded(publicKey), AddressLookupTable.FACTORY)
            .join();
    log.info(accountInfo.toString());
  }

  public OrcAllPoolsInfoResp getAllPoolsInfoViaOrcApi() {
    try {
      OrcAllPoolsInfoResp resp =
          restClient
              .get()
              .uri(ORC_API_URL + GET_ALL_POOLS_INFO_URL)
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

  public OrcPoolInfoResp getPoolInfoViaOrcApi(@NonNull String publicKey) {
    try {
      OrcPoolInfoResp resp =
          restClient
              .get()
              .uri(ORC_API_URL + GET_POOL + publicKey)
              .header("Content-Type", "application/json")
              .retrieve()
              .body(OrcPoolInfoResp.class);
      log.info(resp != null ? resp.toString() : "Response is null");
      return resp;
    } catch (HttpClientErrorException e) {
      log.error(e.getMessage());
    }
    return null;
  }

  /**
   * Симуляция транзакции обмена (упрощённый вариант). Если вы используете mainnet, убедитесь, что
   * SolanaRpcClient настроен на mainnet: "https://api.mainnet-beta.solana.com"
   */
  public String simulateSwapTransaction(long inputAmount) {
    // Простейшая логика: minOutput = 0
    long minOutput = 0;
    byte[] swapData = createSwapInstructionData(inputAmount, minOutput);

    // Адрес пула SOL–USDC
    PublicKey poolPubKey = PublicKey.fromBase58Encoded(SOL_USDC_POOL_ADDRESS);
    // Подписант
    Signer signer = Signer.createFromPrivateKey(MY_WALLET_PRIVATE_KEY.getBytes());
    PublicKey signerPubKey = signer.publicKey();

    // Orca Whirlpool Program ID
    PublicKey orcaProgramId = PublicKey.fromBase58Encoded(ORCA_WHIRLPOOL_PROGRAM_ID);

    // Пример: добавим mint wSOL и mint USDC как read-only аккаунты,
    // если вы делаете обмен SOL->USDC
    PublicKey wsolPubKey = PublicKey.fromBase58Encoded(WSOL_MINT);
    PublicKey usdcPubKey = PublicKey.fromBase58Encoded(USDC_MINT);

    // Формируем список аккаунтов (минимальный пример, реальный swap требует больше аккаунтов)
    List<AccountMeta> accounts = new ArrayList<>();
    // Подписант (writable, signer)
    accounts.add(AccountMeta.createWritableSigner(signerPubKey));
    // Аккаунт пула (read-only)
    accounts.add(AccountMeta.createRead(poolPubKey));
    // Mint wSOL
    accounts.add(AccountMeta.createRead(wsolPubKey));
    // Mint USDC
    accounts.add(AccountMeta.createRead(usdcPubKey));

    Instruction swapInstruction = Instruction.createInstruction(orcaProgramId, accounts, swapData);
    List<Instruction> instructions = List.of(swapInstruction);

    // Создаем транзакцию
    AccountMeta feePayer = AccountMeta.createFeePayer(signerPubKey);
    NativeProgramClient nativeProgramClient = NativeProgramClient.createClient();
    NativeProgramAccountClient accountClient = nativeProgramClient.createAccountClient(feePayer);

    Transaction transaction =
        accountClient.createTransaction(ComputeBudgetProgram.MAX_COMPUTE_BUDGET, 0, instructions);

    // Для симуляции используем адрес вашего кошелька
    PublicKey simulationPubKey = PublicKey.fromBase58Encoded(MY_WALLET_ADDRESS);
    TxSimulation simulationResult =
        rpcClient
            .simulateTransaction(transaction, simulationPubKey, List.of(simulationPubKey))
            .join();

    log.info("Результат симуляции транзакции: {}", simulationResult);
    return "Результат симуляции транзакции: " + simulationResult;
  }

  private byte[] createSwapInstructionData(long inputAmount, long minOutput) {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 2);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.putLong(inputAmount);
    buffer.putLong(minOutput);
    return buffer.array();
  }
}
