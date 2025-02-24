package com.crypto.arbitrage.providers.orc;

import com.crypto.arbitrage.providers.orc.model.OrcAllPoolsInfoResp;
import com.crypto.arbitrage.providers.orc.model.OrcPoolInfoResp;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
import software.sava.core.encoding.Base58;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.SolanaNetwork;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.LatestBlockHash;
import software.sava.solana.programs.clients.NativeProgramAccountClient;
import software.sava.solana.programs.clients.NativeProgramClient;


@Slf4j
@Service
@RequiredArgsConstructor
public class OrcProvider {

  // Программа Orca Whirlpool
  private static final String RADYIUM_AMM_PROGRAM_ID =
      "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8";

  // Адрес пула SOL–USDC (mainnet) RADYIUM
  private static final String SOL_POPCAT_POOL_ADDRESS =
      "FRhB8L7Y9Qq41qZXYLtC2nw8An1RJfLLxRF2x9RwLLMo";

  private static final String MARKET_PROGRAM_ID = "srmtest111111111111111111111111111111111111111"; // OpenBook/Serum Market


  // Адрес wSOL (wrapped SOL) mint на mainnet
  // (используется, если вы меняете SOL -> USDC)
  private static final String WSOL_MINT = "So11111111111111111111111111111111111111112";

  // Адрес USDC mint на mainnet
  private static final String USDC_MINT = "EPjFWdd5AufqSSqeM2q8E3ft6Gbv3ZYwfyAbvZz49Hdj";
  private static final String POPCAT_MINT = "7GCihgDB8fe6KNjn2MYtkzZcRjQy3t9GHdC8uHYmW2hr";

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
//   */
//  public String simulateSwapTransaction(long inputAmount) {
//    // Простейшая логика: minOutput = 0
//    long minOutput = 1;
//    byte[] swapData = createSwapInstructionData(inputAmount, minOutput);
//
//    // Адрес пула SOL–USDC
//    PublicKey poolPubKey = PublicKey.fromBase58Encoded(SOL_USDC_POOL_ADDRESS);
//    // Подписант
//    Signer signer = Signer.createFromPrivateKey(MY_WALLET_PRIVATE_KEY.getBytes());
//    PublicKey signerPubKey = signer.publicKey();
//
//    // Orca Whirlpool Program ID
//    PublicKey orcaProgramId = PublicKey.fromBase58Encoded(ORCA_WHIRLPOOL_PROGRAM_ID);
//
//    // Пример: добавим mint wSOL и mint USDC как read-only аккаунты,
//    // если вы делаете обмен SOL->USDC
//    PublicKey wsolPubKey = PublicKey.fromBase58Encoded(WSOL_MINT);
//    PublicKey usdcPubKey = PublicKey.fromBase58Encoded(USDC_MINT);
//
//    // Формируем список аккаунтов (минимальный пример, реальный swap требует больше аккаунтов)
//    List<AccountMeta> accounts = new ArrayList<>();
//    // Подписант (writable, signer)
//    accounts.add(AccountMeta.createWritableSigner(signerPubKey));
//    // Аккаунт пула (read-only)
//    accounts.add(AccountMeta.createRead(poolPubKey));
//    // Mint wSOL
//    accounts.add(AccountMeta.createRead(wsolPubKey));
//    // Mint USDC
//    accounts.add(AccountMeta.createRead(usdcPubKey));
//
//    Instruction swapInstruction = Instruction.createInstruction(orcaProgramId, accounts, swapData);
//    List<Instruction> instructions = List.of(swapInstruction);
//
//    // Создаем транзакцию
//    AccountMeta feePayer = AccountMeta.createFeePayer(signerPubKey);
//    NativeProgramClient nativeProgramClient = NativeProgramClient.createClient();
//    NativeProgramAccountClient accountClient = nativeProgramClient.createAccountClient(feePayer);
//
//    Transaction transaction =
//        accountClient.createTransaction(ComputeBudgetProgram.MAX_COMPUTE_BUDGET, 0, instructions);
//
//    // Для симуляции используем адрес вашего кошелька
//    PublicKey simulationPubKey = PublicKey.fromBase58Encoded(MY_WALLET_ADDRESS);
//    TxSimulation simulationResult =
//        rpcClient
//            .simulateTransaction(transaction, simulationPubKey, List.of(simulationPubKey))
//            .join();
//
//    log.info("Результат симуляции транзакции: {}", simulationResult);
//    return "Результат симуляции транзакции: " + simulationResult;
//  }

  public String executeSellSolTransaction() throws Exception {
    HttpClient httpClient = HttpClient.newHttpClient();
    SolanaRpcClient rpcClient = SolanaRpcClient.createClient(SolanaNetwork.MAIN_NET.getEndpoint(), httpClient);
    Signer signer = Signer.createFromPrivateKey(Base58.decode(MY_WALLET_PRIVATE_KEY));
    PublicKey signerPubKey = signer.publicKey();

    // Получаем адрес токен-аккаунта для USDC
    PublicKey poolPubKey = PublicKey.fromBase58Encoded(SOL_POPCAT_POOL_ADDRESS);

    // Определяем количество SOL для продажи (эквивалент $2)
    BigDecimal amountInSol = new BigDecimal("0.01");
    long amountInLamports = amountInSol.multiply(new BigDecimal("1000000000")).longValue();

    byte[] swapData = createSwapInstructionData(amountInLamports, 0);

    List<AccountMeta> accounts = new ArrayList<>();
    accounts.add(AccountMeta.createWritableSigner(signerPubKey));  // Подписант
    accounts.add(AccountMeta.createRead(poolPubKey));  // Пул
    accounts.add(AccountMeta.createRead(PublicKey.fromBase58Encoded(WSOL_MINT)));  // SOL Mint
    accounts.add(AccountMeta.createRead(PublicKey.fromBase58Encoded(POPCAT_MINT))); // Popcat Mint
    Instruction swapInstruction = Instruction.createInstruction(
            PublicKey.fromBase58Encoded(RADYIUM_AMM_PROGRAM_ID),
            accounts,
            swapData
    );

    List<Instruction> instructions = List.of(swapInstruction);

    // Создаем транзакцию
    AccountMeta feePayer = AccountMeta.createFeePayer(signerPubKey);
    NativeProgramClient nativeProgramClient = NativeProgramClient.createClient();
    NativeProgramAccountClient accountClient = nativeProgramClient.createAccountClient(feePayer);


    Transaction transaction = accountClient.createTransaction(instructions);

    transaction.sign(signer);
    CompletableFuture<LatestBlockHash> latestBlockHash = rpcClient.getLatestBlockHash();
    LatestBlockHash latestBlockHash1 = latestBlockHash.get();
    transaction.setRecentBlockHash(latestBlockHash1.blockHash());

    byte[] blockhashBytes = Base58.decode(latestBlockHash1.blockHash());

    // Отправляем транзакцию
    try {

      String transactionSignature = rpcClient.sendTransaction(transaction, signer, blockhashBytes).get();
      System.out.println("Транзакция отправлена: " + transactionSignature);
      return transactionSignature;
    } catch (Exception e) {
        System.out.println("Ошибка отправки транзакции: " + e.getMessage());
        throw e;
    }
  }



  public  byte[] createSwapInstructionData(long inputAmount, long minOutput) {
    ByteBuffer buffer = ByteBuffer.allocate(1 + 8 + 8); // 1 byte for instruction + 2 x u64 (8 bytes each)
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    buffer.put((byte) 9); // Instruction ID 9 (Raydium Direct Swap)
    buffer.putLong(inputAmount); // Input amount in lamports
    buffer.putLong(minOutput); // Minimum output amount in lamports

    return buffer.array();
  }


  public static LiquidityPoolKeys getPoolKeys(String ammId, SolanaRpcClient rpcClient) throws Exception {
    PublicKey ammPubKey = PublicKey.fromBase58Encoded(ammId);

    // 1️⃣ Fetch AMM account
    CompletableFuture<AccountInfo<byte[]>> ammAccountFuture = rpcClient.getAccountInfo(ammPubKey);
    AccountInfo<byte[]> ammAccount = ammAccountFuture.get();
    if (ammAccount == null || ammAccount.data() == null) {
      throw new RuntimeException("AMM Pool not found: " + ammId);
    }

    // 2️⃣ Decode `LIQUIDITY_STATE_LAYOUT_V4`
    ByteBuffer buffer = ByteBuffer.wrap(ammAccount.data());
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    PublicKey baseMint = readPublicKey(buffer);
    PublicKey quoteMint = readPublicKey(buffer);
    PublicKey openOrders = readPublicKey(buffer);
    PublicKey baseVault = readPublicKey(buffer);
    PublicKey quoteVault = readPublicKey(buffer);
    PublicKey targetOrders = readPublicKey(buffer);
    PublicKey lpMint = readPublicKey(buffer);
    PublicKey marketId = readPublicKey(buffer);
    int baseDecimals = Byte.toUnsignedInt(buffer.get());
    int quoteDecimals = Byte.toUnsignedInt(buffer.get());

    // 3️⃣ Fetch Market account
    CompletableFuture<AccountInfo<byte[]>> marketAccountFuture = rpcClient.getAccountInfo(marketId);
    AccountInfo<byte[]> marketAccount = marketAccountFuture.get();

    if (marketAccount == null || marketAccount.data() == null) {
      throw new RuntimeException("Market not found: " + marketId.toBase58());
    }

    // 4️⃣ Decode `MARKET_STATE_LAYOUT_V3`
    ByteBuffer marketBuffer = ByteBuffer.wrap(marketAccount.data());
    marketBuffer.order(ByteOrder.LITTLE_ENDIAN);

    PublicKey marketOwnAddress = readPublicKey(marketBuffer);
    PublicKey marketBids = readPublicKey(marketBuffer);
    PublicKey marketAsks = readPublicKey(marketBuffer);
    PublicKey marketEventQueue = readPublicKey(marketBuffer);
    PublicKey marketBaseVault = readPublicKey(marketBuffer);
    PublicKey marketQuoteVault = readPublicKey(marketBuffer);
    long vaultSignerNonce = marketBuffer.getLong();

    // 5️⃣ Compute Market Authority
    // Convert marketOwnAddress and vaultSignerNonce to byte arrays
    List<byte[]> seeds = new ArrayList<>();
    seeds.add(marketOwnAddress.toBytes());
    seeds.add(longToLittleEndian(vaultSignerNonce));

// Compute the Market Authority using PDA
    PublicKey marketAuthority = PublicKey.findProgramAddress(seeds, PublicKey.fromBase58Encoded(MARKET_PROGRAM_ID)).publicKey();

    // 6️⃣ Return Liquidity Pool Info
    return new LiquidityPoolKeys(
            ammPubKey, PublicKey.fromBase58Encoded(RADYIUM_AMM_PROGRAM_ID),
            baseMint, quoteMint, baseVault, quoteVault,
            marketId, marketBids, marketAsks, marketEventQueue,
            marketBaseVault, marketQuoteVault, marketAuthority,
            openOrders, targetOrders, lpMint, baseDecimals, quoteDecimals
    );
  }

  private static PublicKey readPublicKey(ByteBuffer buffer) {
    byte[] keyBytes = new byte[32];
    buffer.get(keyBytes);
    return PublicKey.fromBase58Encoded(Base58.encode(keyBytes));
  }

  private static PublicKey longToLittleEndian(long value) {
    ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value);
    return PublicKey.fromBase58Encoded(Base58.encode(buffer.array()));
  }

  public static class LiquidityPoolKeys {
    public PublicKey id, programId, baseMint, quoteMint, baseVault, quoteVault, marketId;
    public PublicKey marketBids, marketAsks, marketEventQueue, marketBaseVault, marketQuoteVault, marketAuthority;
    public PublicKey openOrders, targetOrders, lpMint;
    public int baseDecimals, quoteDecimals;

    public LiquidityPoolKeys(PublicKey id, PublicKey programId, PublicKey baseMint, PublicKey quoteMint,
                             PublicKey baseVault, PublicKey quoteVault, PublicKey marketId,
                             PublicKey marketBids, PublicKey marketAsks, PublicKey marketEventQueue,
                             PublicKey marketBaseVault, PublicKey marketQuoteVault, PublicKey marketAuthority,
                             PublicKey openOrders, PublicKey targetOrders, PublicKey lpMint,
                             int baseDecimals, int quoteDecimals) {
      this.id = id;
      this.programId = programId;
      this.baseMint = baseMint;
      this.quoteMint = quoteMint;
      this.baseVault = baseVault;
      this.quoteVault = quoteVault;
      this.marketId = marketId;
      this.marketBids = marketBids;
      this.marketAsks = marketAsks;
      this.marketEventQueue = marketEventQueue;
      this.marketBaseVault = marketBaseVault;
      this.marketQuoteVault = marketQuoteVault;
      this.marketAuthority = marketAuthority;
      this.openOrders = openOrders;
      this.targetOrders = targetOrders;
      this.lpMint = lpMint;
      this.baseDecimals = baseDecimals;
      this.quoteDecimals = quoteDecimals;
    }

    @Override
    public String toString() {
      return "LiquidityPoolKeys{" +
              "id=" + id.toBase58() +
              ", programId=" + programId.toBase58() +
              ", baseMint=" + baseMint.toBase58() +
              ", quoteMint=" + quoteMint.toBase58() +
              ", baseVault=" + baseVault.toBase58() +
              ", quoteVault=" + quoteVault.toBase58() +
              ", marketId=" + marketId.toBase58() +
              ", marketBids=" + marketBids.toBase58() +
              ", marketAsks=" + marketAsks.toBase58() +
              ", marketEventQueue=" + marketEventQueue.toBase58() +
              ", marketBaseVault=" + marketBaseVault.toBase58() +
              ", marketQuoteVault=" + marketQuoteVault.toBase58() +
              ", marketAuthority=" + marketAuthority.toBase58() +
              ", openOrders=" + openOrders.toBase58() +
              ", targetOrders=" + targetOrders.toBase58() +
              ", lpMint=" + lpMint.toBase58() +
              ", baseDecimals=" + baseDecimals +
              ", quoteDecimals=" + quoteDecimals +
              '}';
    }
  }

}
