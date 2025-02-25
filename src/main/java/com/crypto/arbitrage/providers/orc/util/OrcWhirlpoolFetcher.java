package com.crypto.arbitrage.providers.orc.util;

import com.crypto.arbitrage.providers.orc.model.RewardInfo;
import com.crypto.arbitrage.providers.orc.model.transaction.OrcWhirlpool;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.Base58;
import software.sava.rpc.json.http.client.SolanaRpcClient;

@Component
@RequiredArgsConstructor
public class OrcWhirlpoolFetcher {

  private final SolanaRpcClient rpcClient;

  /**
   * Fetch and deserialize the Whirlpool account data.
   *
   * <p>IMPORTANT: Replace the offsets and lengths below with the actual ones from the official Rust
   * SDK.
   */
  public OrcWhirlpool fetchWhirlpool(String poolAddress) throws Exception {
    PublicKey poolKey = PublicKey.fromBase58Encoded(poolAddress);
    var accountInfo = rpcClient.getAccountInfo(poolKey).join();
    if (accountInfo == null || accountInfo.data() == null) {
      throw new Exception("Pool not found or no data available.");
    }

    byte[] data = accountInfo.data();
    ByteBuffer buffer = ByteBuffer.wrap(data);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    OrcWhirlpool pool = new OrcWhirlpool();

    // 🟢 1️⃣ Parse `whirlpoolsConfig` (PublicKey)
    pool.setWhirlpoolsConfig(readPublicKey(buffer));

    //  Parse `whirlpoolBump` (1 byte)
    pool.setWhirlpoolBump(buffer.get());

    //  Parse `tickSpacing` (2 bytes, unsigned short)
    pool.setTickSpacing(Short.toUnsignedInt(buffer.getShort()));

    //  Parse `liquidity` (16 bytes, BigInteger)
    pool.setLiquidity(readBigInteger(buffer, 16));

    // Parse `sqrtPrice` (16 bytes, BigInteger)
    pool.setSqrtPrice(readBigInteger(buffer, 16));

    // Parse `tickCurrentIndex` (4 bytes, int)
    pool.setTickCurrentIndex(buffer.getInt());

    //  Parse `feeGrowthGlobalA` (8 bytes, BigInteger)
    pool.setFeeGrowthGlobalA(readBigInteger(buffer, 8));

    //  Parse `feeGrowthGlobalB` (8 bytes, BigInteger)
    pool.setFeeGrowthGlobalB(readBigInteger(buffer, 8));

    // Parse `protocolFeeRate` (1 byte, unsigned)
    pool.setProtocolFeeRate(Byte.toUnsignedInt(buffer.get()));

    // Parse `tokenMintA` (PublicKey)
    pool.setTokenMintA(readPublicKey(buffer));

    //  Parse `tokenVaultA` (PublicKey)
    pool.setTokenVaultA(readPublicKey(buffer));

    // Parse `feeGrowthGlobalA` (8 bytes, BigInteger)
    pool.setFeeGrowthGlobalA(readBigInteger(buffer, 8));

    // Parse `tokenMintB` (PublicKey)
    pool.setTokenMintB(readPublicKey(buffer));

    //  Parse `tokenVaultB` (PublicKey)
    pool.setTokenVaultB(readPublicKey(buffer));

    //  Parse `feeGrowthGlobalB` (8 bytes, BigInteger)
    pool.setFeeGrowthGlobalB(readBigInteger(buffer, 8));

    // Parse `rewardInfos` (Array of RewardInfo)
    for (int i = 0; i < 3; i++) {
      pool.getRewardInfos().add(readRewardInfo(buffer));
    }

    return pool;
    }


    /**
     * Reads a 32-byte public key from the buffer.
     */
  private String readPublicKey(ByteBuffer buffer) {
    byte[] keyBytes = new byte[32];
    buffer.get(keyBytes);
    return PublicKey.fromBase58Encoded(Base58.encode(keyBytes)).toBase58();
  }

  /**
   * Reads a BigInteger from the buffer with the given byte length.
   */
  private BigInteger readBigInteger(ByteBuffer buffer, int size) {
    byte[] bigIntBytes = new byte[size];
    buffer.get(bigIntBytes);
    return new BigInteger(1, bigIntBytes);
  }

  /**
   * Reads `RewardInfo` (32 bytes public key + 16 bytes BigInteger)
   */
  private RewardInfo readRewardInfo(ByteBuffer buffer) {
    RewardInfo rewardInfo = new RewardInfo();
    rewardInfo.setMint(readPublicKey(buffer)); // 32 bytes for mint
    rewardInfo.setVault(readPublicKey(buffer)); // 32 bytes for vault
    rewardInfo.setGrowthGlobal(readBigInteger(buffer, 16)); // 16 bytes
    return rewardInfo;
  }
}
