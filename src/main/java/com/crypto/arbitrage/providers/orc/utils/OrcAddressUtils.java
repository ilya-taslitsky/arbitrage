package com.crypto.arbitrage.providers.orc.utils;

import static com.crypto.arbitrage.providers.orc.constants.OrcConstants.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import software.sava.core.accounts.PublicKey;

/**
 * Utilities for deriving Orca Whirlpool related addresses. Equivalent to address derivation
 * functions in the Rust SDK.
 */
@Slf4j
public class OrcAddressUtils {

  /**
   * Derives the address of a Whirlpool for a given token pair and tick spacing.
   *
   * @param configAddress The Whirlpools config address
   * @param tokenMintA First token mint (must be lexicographically smaller than tokenMintB)
   * @param tokenMintB Second token mint
   * @param tickSpacing The tick spacing for the pool
   * @return The derived Whirlpool address
   */
  public static String getWhirlpoolAddress(
      String configAddress, String tokenMintA, String tokenMintB, int tickSpacing) {

    try {
      // Verify token order
      if (tokenMintA.compareTo(tokenMintB) > 0) {
        throw new IllegalArgumentException("Token mints must be in correct order (A < B)");
      }

      PublicKey programId = WHIRLPOOL_PROGRAM_ID;
      PublicKey configKey = PublicKey.fromBase58Encoded(configAddress);
      PublicKey mintAKey = PublicKey.fromBase58Encoded(tokenMintA);
      PublicKey mintBKey = PublicKey.fromBase58Encoded(tokenMintB);

      List<byte[]> seeds = new ArrayList<>();
      seeds.add("whirlpool".getBytes(StandardCharsets.UTF_8));
      seeds.add(configKey.toByteArray());
      seeds.add(mintAKey.toByteArray());
      seeds.add(mintBKey.toByteArray());
      seeds.add(encodeTickSpacing(tickSpacing));

      var pda = PublicKey.findProgramAddress(seeds, programId);
      return pda.publicKey().toBase58();
    } catch (Exception e) {
      log.error("Error deriving whirlpool address: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to derive whirlpool address", e);
    }
  }

  /**
   * Derives the address of a tick array for a given Whirlpool.
   *
   * @param whirlpoolAddress The address of the Whirlpool
   * @param startTickIndex The start tick index for the tick array
   * @return The derived tick array address
   */
  public static String getTickArrayAddress(String whirlpoolAddress, int startTickIndex) {
    try {
      PublicKey whirlpoolKey = PublicKey.fromBase58Encoded(whirlpoolAddress);

      List<byte[]> seeds = new ArrayList<>();
      seeds.add("tick_array".getBytes(StandardCharsets.UTF_8));
      seeds.add(String.valueOf(startTickIndex).getBytes(StandardCharsets.UTF_8));
      seeds.add(whirlpoolKey.toByteArray());

      var pda = PublicKey.findProgramAddress(seeds, WHIRLPOOL_PROGRAM_ID);
      return pda.publicKey().toBase58();
    } catch (Exception e) {
      log.error("Error deriving tick array address: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to derive tick array address", e);
    }
  }

  /**
   * Derives the oracle address for a Whirlpool.
   *
   * @param whirlpoolAddress The address of the Whirlpool
   * @return The derived oracle address
   */
  public static String getOracleAddress(String whirlpoolAddress) {
    try {
      PublicKey whirlpoolKey = PublicKey.fromBase58Encoded(whirlpoolAddress);

      List<byte[]> seeds = new ArrayList<>();
      seeds.add("oracle".getBytes(StandardCharsets.UTF_8));
      seeds.add(whirlpoolKey.toByteArray());

      var pda = PublicKey.findProgramAddress(seeds, WHIRLPOOL_PROGRAM_ID);
      return pda.publicKey().toBase58();
    } catch (Exception e) {
      log.error("Error deriving oracle address: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to derive oracle address", e);
    }
  }

  /**
   * Derives the position address for a given position mint.
   *
   * @param positionMint The mint address of the position NFT
   * @return The derived position address
   */
  public static String getPositionAddress(String positionMint) {
    try {
      PublicKey mintKey = PublicKey.fromBase58Encoded(positionMint);

      List<byte[]> seeds = new ArrayList<>();
      seeds.add("position".getBytes(StandardCharsets.UTF_8));
      seeds.add(mintKey.toByteArray());

      var pda = PublicKey.findProgramAddress(seeds, WHIRLPOOL_PROGRAM_ID);
      return pda.publicKey().toBase58();
    } catch (Exception e) {
      log.error("Error deriving position address: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to derive position address", e);
    }
  }

  /**
   * Gets the associated token address for a wallet and mint.
   *
   * @param walletAddress The wallet address
   * @param mintAddress The mint address
   * @return The associated token address
   */
  public static String getAssociatedTokenAddress(String walletAddress, String mintAddress) {
    try {
      PublicKey walletKey = PublicKey.fromBase58Encoded(walletAddress);
      PublicKey mintKey = PublicKey.fromBase58Encoded(mintAddress);

      List<byte[]> seeds = new ArrayList<>();
      seeds.add(walletKey.toByteArray());
      seeds.add(TOKEN_PROGRAM_ID.toByteArray());
      seeds.add(mintKey.toByteArray());

      var pda = PublicKey.findProgramAddress(seeds, ASSOCIATED_TOKEN_PROGRAM_ID);
      return pda.publicKey().toBase58();
    } catch (Exception e) {
      log.error("Error deriving associated token address: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to derive associated token address", e);
    }
  }

  /**
   * Calculates the start tick index for a tick array containing the given tick index.
   *
   * @param tickIndex The tick index to locate
   * @param tickSpacing The tick spacing of the pool
   * @return The start tick index for the tick array
   */
  public static int getTickArrayStartTickIndex(int tickIndex, int tickSpacing) {
    int ticksPerArray = TICK_ARRAY_SIZE * tickSpacing;
    int realIndex = Math.floorDiv(tickIndex, ticksPerArray);
    return realIndex * ticksPerArray;
  }

  /**
   * Gets tick array addresses needed for a swap based on the current tick index.
   *
   * @param whirlpoolAddress The pool address
   * @param currentTickIndex The current tick index of the pool
   * @param tickSpacing The tick spacing of the pool
   * @param aToB Direction of swap (A to B or B to A)
   * @return An array of tick array addresses
   */
  public static String[] getTickArrayAddressesForSwap(
      String whirlpoolAddress, int currentTickIndex, int tickSpacing, boolean aToB) {

    // Calculate the start tick index of the current tick array
    int currentArrayStartTick = getTickArrayStartTickIndex(currentTickIndex, tickSpacing);
    int ticksPerArray = TICK_ARRAY_SIZE * tickSpacing;

    // Determine which additional arrays we need based on swap direction
    String[] tickArrays = new String[3];

    if (aToB) {
      // When swapping A to B (price going down), we need the current and two arrays below
      tickArrays[0] = getTickArrayAddress(whirlpoolAddress, currentArrayStartTick);
      tickArrays[1] = getTickArrayAddress(whirlpoolAddress, currentArrayStartTick - ticksPerArray);
      tickArrays[2] =
          getTickArrayAddress(whirlpoolAddress, currentArrayStartTick - 2 * ticksPerArray);
    } else {
      // When swapping B to A (price going up), we need the current and two arrays above
      tickArrays[0] = getTickArrayAddress(whirlpoolAddress, currentArrayStartTick);
      tickArrays[1] = getTickArrayAddress(whirlpoolAddress, currentArrayStartTick + ticksPerArray);
      tickArrays[2] =
          getTickArrayAddress(whirlpoolAddress, currentArrayStartTick + 2 * ticksPerArray);
    }

    return tickArrays;
  }

  /**
   * Encodes a tick spacing as a byte array.
   *
   * @param tickSpacing The tick spacing value
   * @return The encoded byte array
   */
  private static byte[] encodeTickSpacing(int tickSpacing) {
    byte[] result = new byte[2];
    result[0] = (byte) (tickSpacing & 0xFF);
    result[1] = (byte) ((tickSpacing >> 8) & 0xFF);
    return result;
  }

  /**
   * Orders two token mints lexicographically.
   *
   * @param mintA First token mint
   * @param mintB Second token mint
   * @return An array with mints in lexicographical order [smaller, larger]
   */
  public static String[] orderMints(String mintA, String mintB) {
    if (mintA.compareTo(mintB) < 0) {
      return new String[] {mintA, mintB};
    } else {
      return new String[] {mintB, mintA};
    }
  }
}
