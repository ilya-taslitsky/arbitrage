package com.crypto.arbitrage.providers.orc.services;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.OrcTickArray;
import com.crypto.arbitrage.providers.orc.models.OrcWhirlpool;
import com.crypto.arbitrage.providers.orc.utils.OrcAddressUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Extension method for OrcAccountFetcher to support tick array fetching. This should be added to
 * your existing OrcAccountFetcher class.
 */
@Slf4j
@Service
public class OrcAccountFetcherExtension {

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
}
