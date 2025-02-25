package com.crypto.arbitrage.providers.orc.util;

import com.crypto.arbitrage.providers.orc.model.transaction.OrcTick;
import com.crypto.arbitrage.providers.orc.model.transaction.OrcTickArray;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.springframework.stereotype.Component;
import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.client.SolanaRpcClient;

@Component
public class OrcTickArrayFetcher {

  private static final String ORCA_WHIRLPOOL_PROGRAM_ID =
      "whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc";
  private static final String DERIVE_TICK_ARRAY_ADDRESS =
      "11111111111111111111111111111111"; // dummy address

  /**
   * Fetches and deserializes tick array accounts.
   *
   * <p>IMPORTANT: In a real implementation you must derive the tick array addresses based on the
   * pool’s current tick index and tick spacing. Here we assume 5 arrays.
   */
  public OrcTickArray[] fetchTickArrays(
      SolanaRpcClient rpcClient,
      com.crypto.arbitrage.providers.orc.model.transaction.OrcWhirlpool pool) {
    OrcTickArray[] tickArrays = new OrcTickArray[5];
    for (int i = 0; i < tickArrays.length; i++) {
      // Derive the tick array address (this must follow the program’s PDA derivation rules)
      String tickArrayAddress = deriveTickArrayAddress(pool, i);
      var accountInfo =
          rpcClient.getAccountInfo(PublicKey.fromBase58Encoded(tickArrayAddress)).join();
      if (accountInfo == null) {
        // If tick array does not exist on–chain, create a default uninitialized tick array
        tickArrays[i] = createDefaultTickArray(i);
      } else {
        // Use accountInfo.data() instead of accountInfo.getData()
        tickArrays[i] = deserializeTickArray((byte[]) accountInfo.data());
      }
    }
    return tickArrays;
  }

  private String deriveTickArrayAddress(
      com.crypto.arbitrage.providers.orc.model.transaction.OrcWhirlpool pool, int index) {
    // TODO: Implement PDA derivation according to the Orca Whirlpool spec.
    return DERIVE_TICK_ARRAY_ADDRESS;
  }

  private OrcTickArray createDefaultTickArray(int index) {
    OrcTickArray tickArray = new OrcTickArray();
    tickArray.setAddress(DERIVE_TICK_ARRAY_ADDRESS + index);
    tickArray.setStartTickIndex(index * 88); // for example, if each array covers 88 ticks
    OrcTick[] ticks = new OrcTick[88];
    for (int j = 0; j < ticks.length; j++) {
      OrcTick tick = new OrcTick();
      tick.setInitialized(false);
      // Set other fields to zero/default values
      tick.setLiquidityNet("0");
      tick.setLiquidityGross("0");
      tick.setFeeGrowthOutsideA("0");
      tick.setFeeGrowthOutsideB("0");
      tick.setRewardGrowthsOutside(new String[] {"0", "0", "0"});
      ticks[j] = tick;
    }
    tickArray.setTicks(ticks);
    tickArray.setExists(false);
    tickArray.setProgramAddress(ORCA_WHIRLPOOL_PROGRAM_ID);
    return tickArray;
  }

  private OrcTickArray deserializeTickArray(byte[] data) {
    OrcTickArray tickArray = new OrcTickArray();
    ByteBuffer buffer = ByteBuffer.wrap(data);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    // Check if we have at least 4 bytes for the startTickIndex.
    if (buffer.remaining() < 4) {
      // Not enough data – return a default tick array or handle the error as needed.
      return createDefaultTickArray(0);
    }
    tickArray.setStartTickIndex(buffer.getInt());

    // Define the expected bytes per tick.
    final int bytesPerTick = 1 + 8; // 1 byte for the flag + 8 bytes for liquidityNet.
    final int expectedTicks = 88;
    final int expectedTicksBytes = expectedTicks * bytesPerTick;

    // Check if the remaining bytes are sufficient for the ticks.
    if (buffer.remaining() < expectedTicksBytes) {
      // Not enough data – return a default tick array or handle the error as needed.
      return createDefaultTickArray(0);
    }

    OrcTick[] ticks = new OrcTick[expectedTicks];
    for (int i = 0; i < ticks.length; i++) {
      OrcTick tick = new OrcTick();
      // Read 1 byte for the "initialized" flag.
      tick.setInitialized(buffer.get() != 0);

      // Read 8 bytes for liquidityNet.
      byte[] liquidityNetBytes = new byte[8];
      buffer.get(liquidityNetBytes);
      tick.setLiquidityNet(new java.math.BigInteger(1, liquidityNetBytes).toString());

      // If you have additional fields (liquidityGross, feeGrowthOutsideA, etc.),
      // check buffer.remaining() before reading each field.
      ticks[i] = tick;
    }
    tickArray.setTicks(ticks);
    tickArray.setExists(true);
    tickArray.setAddress("DeserializedTickArrayAddress");
    tickArray.setProgramAddress(ORCA_WHIRLPOOL_PROGRAM_ID);
    return tickArray;
  }
}
