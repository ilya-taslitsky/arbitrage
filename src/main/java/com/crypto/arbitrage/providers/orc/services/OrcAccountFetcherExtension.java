package com.crypto.arbitrage.providers.orc.services;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.OrcTick;
import com.crypto.arbitrage.providers.orc.models.OrcTickArray;
import com.crypto.arbitrage.providers.orc.models.OrcWhirlpool;
import com.crypto.arbitrage.providers.orc.utils.OrcAddressUtils;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.client.SolanaRpcClient;

/** Extension method for OrcAccountFetcher to support tick array fetching. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrcAccountFetcherExtension {

  private final SolanaRpcClient rpcClient;

  /**
   * Fetch all tick arrays needed for a swap with better error handling.
   *
   * @param pool The Whirlpool to fetch tick arrays for
   * @param aToB Direction of the swap
   * @return An array of TickArray objects
   */
  public OrcTickArray[] fetchTickArraysForSwap(OrcWhirlpool pool, boolean aToB) {
    try {
      String[] tickArrayAddresses =
          OrcAddressUtils.getTickArrayAddressesForSwap(
              pool.getAddress(), pool.getTickCurrentIndex(), pool.getTickSpacing(), aToB);

      OrcTickArray[] tickArrays = new OrcTickArray[tickArrayAddresses.length];

      for (int i = 0; i < tickArrayAddresses.length; i++) {
        try {
          tickArrays[i] = fetchTickArray(tickArrayAddresses[i]);

          // Quick validation of the tick array
          if (tickArrays[i] == null || !tickArrays[i].isInitialized()) {
            log.warn("Tick array {} is not initialized, creating default", tickArrayAddresses[i]);
            int startTickIndex =
                calculateStartTickIndex(pool.getTickCurrentIndex(), pool.getTickSpacing(), i, aToB);
            tickArrays[i] = createDefaultTickArray(tickArrayAddresses[i], startTickIndex);
          }
        } catch (Exception e) {
          log.error("Error fetching tick array {}: {}", tickArrayAddresses[i], e.getMessage());
          int startTickIndex =
              calculateStartTickIndex(pool.getTickCurrentIndex(), pool.getTickSpacing(), i, aToB);
          tickArrays[i] = createDefaultTickArray(tickArrayAddresses[i], startTickIndex);
        }
      }

      return tickArrays;
    } catch (Exception e) {
      log.error("Error fetching tick arrays for swap: {}", e.getMessage(), e);

      // Create default tick arrays as fallback
      OrcTickArray[] defaultArrays = new OrcTickArray[3];
      for (int i = 0; i < 3; i++) {
        int startTickIndex =
            calculateStartTickIndex(pool.getTickCurrentIndex(), pool.getTickSpacing(), i, aToB);
        defaultArrays[i] = createDefaultTickArray("unknown_" + i, startTickIndex);
      }

      return defaultArrays;
    }
  }

  /** Calculate the start tick index for a tick array based on the current tick and offset. */
  private int calculateStartTickIndex(
      int currentTickIndex, int tickSpacing, int arrayOffset, boolean aToB) {
    int ticksPerArray = OrcConstants.TICK_ARRAY_SIZE * tickSpacing;
    int currentArrayStartTick =
        OrcAddressUtils.getTickArrayStartTickIndex(currentTickIndex, tickSpacing);

    if (aToB) {
      // For A to B (price decreasing), we go down in arrays
      return currentArrayStartTick - (arrayOffset * ticksPerArray);
    } else {
      // For B to A (price increasing), we go up in arrays
      return currentArrayStartTick + (arrayOffset * ticksPerArray);
    }
  }

  /**
   * Fetches a tick array needed for swap operations.
   *
   * @param tickArrayAddress The address of the tick array
   * @return The deserialized tick array data, or a default one if not found
   */
  public OrcTickArray fetchTickArray(String tickArrayAddress) {
    try {
      PublicKey tickArrayKey = PublicKey.fromBase58Encoded(tickArrayAddress);
      var accountInfo = rpcClient.getAccountInfo(tickArrayKey).join();

      if (accountInfo == null || accountInfo.data() == null) {
        log.warn("Tick array not found at address: {}", tickArrayAddress);
        return createDefaultTickArray(tickArrayAddress, 0);
      }

      byte[] data = (byte[]) accountInfo.data();
      ByteBuffer buffer = ByteBuffer.wrap(data);
      buffer.order(ByteOrder.LITTLE_ENDIAN);

      // Skip 8-byte discriminator
      buffer.position(8);

      // startTickIndex - i32 (4 bytes)
      int startTickIndex = buffer.getInt();

      // Get the whirlpool (32 bytes)
      byte[] whirlpoolBytes = new byte[32];
      buffer.get(whirlpoolBytes);
      String whirlpoolAddress = PublicKey.createPubKey(whirlpoolBytes).toBase58();

      OrcTickArray tickArray = new OrcTickArray();
      tickArray.setAddress(tickArrayAddress);
      tickArray.setStartTickIndex(startTickIndex);
      tickArray.setInitialized(true);

      OrcTick[] ticks = new OrcTick[OrcConstants.TICK_ARRAY_SIZE];
      for (int i = 0; i < ticks.length; i++) {
        OrcTick tick = new OrcTick();

        // initialized - bool (1 byte)
        tick.setInitialized(buffer.get() != 0);

        // liquidityNet - i128 (16 bytes)
        byte[] liquidityNetBytes = new byte[16];
        buffer.get(liquidityNetBytes);
        tick.setLiquidityNet(new BigInteger(liquidityNetBytes));

        // liquidityGross - u128 (16 bytes)
        byte[] liquidityGrossBytes = new byte[16];
        buffer.get(liquidityGrossBytes);
        tick.setLiquidityGross(new BigInteger(1, liquidityGrossBytes));

        // feeGrowthOutsideA - u128 (16 bytes)
        byte[] feeGrowthOutsideABytes = new byte[16];
        buffer.get(feeGrowthOutsideABytes);
        tick.setFeeGrowthOutsideA(new BigInteger(1, feeGrowthOutsideABytes));

        // feeGrowthOutsideB - u128 (16 bytes)
        byte[] feeGrowthOutsideBBytes = new byte[16];
        buffer.get(feeGrowthOutsideBBytes);
        tick.setFeeGrowthOutsideB(new BigInteger(1, feeGrowthOutsideBBytes));

        // rewardGrowthsOutside - [u128; 3] (3 * 16 bytes)
        BigInteger[] rewardGrowthsOutside = new BigInteger[3];
        for (int j = 0; j < 3; j++) {
          byte[] rewardGrowthBytes = new byte[16];
          buffer.get(rewardGrowthBytes);
          rewardGrowthsOutside[j] = new BigInteger(1, rewardGrowthBytes);
        }
        tick.setRewardGrowthsOutside(rewardGrowthsOutside);

        ticks[i] = tick;
      }

      tickArray.setTicks(ticks);

      return tickArray;
    } catch (Exception e) {
      log.error("Error fetching tick array: {}", e.getMessage(), e);
      return createDefaultTickArray(tickArrayAddress, 0);
    }
  }

  /**
   * Creates a default tick array for when the actual array isn't found on-chain.
   *
   * @param address The address of the tick array
   * @param startTickIndex The start tick index
   * @return A default TickArray object
   */
  private OrcTickArray createDefaultTickArray(String address, int startTickIndex) {
    OrcTick[] ticks = new OrcTick[OrcConstants.TICK_ARRAY_SIZE];
    for (int i = 0; i < ticks.length; i++) {
      ticks[i] =
          new OrcTick(
              false, // initialized
              BigInteger.ZERO, // liquidityNet
              BigInteger.ZERO, // liquidityGross
              BigInteger.ZERO, // feeGrowthOutsideA
              BigInteger.ZERO, // feeGrowthOutsideB
              new BigInteger[] { // rewardGrowthsOutside
                BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO
              });
    }

    return OrcTickArray.builder()
        .address(address)
        .startTickIndex(startTickIndex)
        .ticks(ticks)
        .initialized(false)
        .build();
  }
}
