package com.crypto.arbitrage.providers.orc.util;

import com.crypto.arbitrage.providers.orc.model.transaction.OrcWhirlpool;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.sava.core.accounts.PublicKey;
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
    if (accountInfo == null) {
      throw new Exception("Pool not found");
    }
    byte[] data = accountInfo.data();
    ByteBuffer buffer = ByteBuffer.wrap(data);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    OrcWhirlpool pool = new OrcWhirlpool();

    // Example deserialization – adjust each field per the official layout!
    // For instance, assume:
    // - Bytes [0,32): whirlpoolsConfig (public key)
    byte[] configBytes = new byte[32];
    buffer.get(configBytes);
    pool.setWhirlpoolsConfig(PublicKey.createPubKey(configBytes).toBase58());

    // - Byte 32: whirlpoolBump
    pool.setWhirlpoolBump(buffer.get());

    // - Bytes 33-34: tickSpacing (unsigned short)
    pool.setTickSpacing(Short.toUnsignedInt(buffer.getShort()));

    // - Bytes 35-50: liquidity (16 bytes, BigInteger, unsigned)
    byte[] liquidityBytes = new byte[16];
    buffer.get(liquidityBytes);
    pool.setLiquidity(new BigInteger(1, liquidityBytes));

    // - Bytes 51-66: sqrtPrice (16 bytes, BigInteger)
    byte[] sqrtPriceBytes = new byte[16];
    buffer.get(sqrtPriceBytes);
    pool.setSqrtPrice(new BigInteger(1, sqrtPriceBytes));

    // - Next 4 bytes: tickCurrentIndex (int)
    pool.setTickCurrentIndex(buffer.getInt());

    // - Next 8 bytes each for feeGrowthGlobalA and feeGrowthGlobalB (BigInteger)
    byte[] feeGrowthABytes = new byte[8];
    buffer.get(feeGrowthABytes);
    pool.setFeeGrowthGlobalA(new BigInteger(1, feeGrowthABytes));

    byte[] feeGrowthBBytes = new byte[8];
    buffer.get(feeGrowthBBytes);
    pool.setFeeGrowthGlobalB(new BigInteger(1, feeGrowthBBytes));

    // Continue parsing additional fields as required…
    // (e.g. protocolFeeRate, tokenMintA, tokenVaultA, tokenMintB, tokenVaultB, etc.)

    return pool;
  }
}
