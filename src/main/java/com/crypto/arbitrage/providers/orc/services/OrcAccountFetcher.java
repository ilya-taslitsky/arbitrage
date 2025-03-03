package com.crypto.arbitrage.providers.orc.services;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.*;
import com.crypto.arbitrage.providers.orc.utils.OrcAddressUtils;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.client.SolanaRpcClient;

/** Service for fetching and parsing Orca Whirlpool accounts from the Solana blockchain. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrcAccountFetcher {

  private final SolanaRpcClient rpcClient;

  /**
   * Fetches and deserializes a Whirlpool account.
   *
   * @param poolAddress The address of the Whirlpool
   * @return The deserialized Whirlpool data
   * @throws Exception If there's an error fetching or parsing the account
   */
  public OrcWhirlpool fetchWhirlpool(String poolAddress) throws Exception {
    PublicKey poolKey = PublicKey.fromBase58Encoded(poolAddress);
    var accountInfo = rpcClient.getAccountInfo(poolKey).join();

    if (accountInfo == null) {
      throw new Exception("Pool not found at address: " + poolAddress);
    }

    Object rawData = accountInfo.data();
    if (rawData == null) {
      throw new Exception("No account data found for pool at address: " + poolAddress);
    }

    byte[] data = (byte[]) rawData;
    ByteBuffer buffer = ByteBuffer.wrap(data);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    OrcWhirlpool pool = new OrcWhirlpool();
    pool.setAddress(poolAddress);

    // Skip 8-byte discriminator
    buffer.position(8);

    // whirlpoolsConfig - PublicKey (32 bytes)
    byte[] whirlpoolsConfigBytes = new byte[32];
    buffer.get(whirlpoolsConfigBytes);
    pool.setWhirlpoolsConfig(PublicKey.createPubKey(whirlpoolsConfigBytes).toBase58());

    // whirlpoolBump - u8 (1 byte)
    pool.setWhirlpoolBump(buffer.get());

    // tickSpacing - u16 (2 bytes)
    pool.setTickSpacing(Short.toUnsignedInt(buffer.getShort()));

    // Skip tick spacing seed (2 bytes)
    buffer.position(buffer.position() + 2);

    // feeRate - u16 (2 bytes)
    pool.setFeeRate(Short.toUnsignedInt(buffer.getShort()));

    // protocolFeeRate - u16 (2 bytes)
    pool.setProtocolFeeRate(Short.toUnsignedInt(buffer.getShort()));

    // liquidity - u128 (16 bytes)
    byte[] liquidityBytes = new byte[16];
    buffer.get(liquidityBytes);
    pool.setLiquidity(new BigInteger(1, liquidityBytes));

    // sqrtPrice - u128 (16 bytes)
    byte[] sqrtPriceBytes = new byte[16];
    buffer.get(sqrtPriceBytes);
    pool.setSqrtPrice(new BigInteger(1, sqrtPriceBytes));

    // tickCurrentIndex - i32 (4 bytes)
    pool.setTickCurrentIndex(buffer.getInt());

    // protocolFeeOwedA - u64 (8 bytes)
    byte[] protocolFeeOwedABytes = new byte[8];
    buffer.get(protocolFeeOwedABytes);
    pool.setProtocolFeeOwedA(new BigInteger(1, protocolFeeOwedABytes));

    // protocolFeeOwedB - u64 (8 bytes)
    byte[] protocolFeeOwedBBytes = new byte[8];
    buffer.get(protocolFeeOwedBBytes);
    pool.setProtocolFeeOwedB(new BigInteger(1, protocolFeeOwedBBytes));

    // tokenMintA - PublicKey (32 bytes)
    byte[] tokenMintABytes = new byte[32];
    buffer.get(tokenMintABytes);
    pool.setTokenMintA(PublicKey.createPubKey(tokenMintABytes).toBase58());

    // tokenVaultA - PublicKey (32 bytes)
    byte[] tokenVaultABytes = new byte[32];
    buffer.get(tokenVaultABytes);
    pool.setTokenVaultA(PublicKey.createPubKey(tokenVaultABytes).toBase58());

    // feeGrowthGlobalA - u128 (16 bytes)
    byte[] feeGrowthGlobalABytes = new byte[16];
    buffer.get(feeGrowthGlobalABytes);
    pool.setFeeGrowthGlobalA(new BigInteger(1, feeGrowthGlobalABytes));

    // tokenMintB - PublicKey (32 bytes)
    byte[] tokenMintBBytes = new byte[32];
    buffer.get(tokenMintBBytes);
    pool.setTokenMintB(PublicKey.createPubKey(tokenMintBBytes).toBase58());

    // tokenVaultB - PublicKey (32 bytes)
    byte[] tokenVaultBBytes = new byte[32];
    buffer.get(tokenVaultBBytes);
    pool.setTokenVaultB(PublicKey.createPubKey(tokenVaultBBytes).toBase58());

    // feeGrowthGlobalB - u128 (16 bytes)
    byte[] feeGrowthGlobalBBytes = new byte[16];
    buffer.get(feeGrowthGlobalBBytes);
    pool.setFeeGrowthGlobalB(new BigInteger(1, feeGrowthGlobalBBytes));

    // Store remaining bytes as reward infos (we'll parse them only when needed)
    byte[] rewardInfos = new byte[buffer.remaining()];
    buffer.get(rewardInfos);
    pool.setRewardInfos(rewardInfos);

    return pool;
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
   * Fetch all tick arrays needed for a swap.
   *
   * @param whirlpool The Whirlpool to fetch tick arrays for
   * @param aToB Direction of the swap
   * @return An array of TickArray objects
   */
  public OrcTickArray[] fetchTickArraysForSwap(OrcWhirlpool whirlpool, boolean aToB) {
    String[] tickArrayAddresses =
        OrcAddressUtils.getTickArrayAddressesForSwap(
            whirlpool.getAddress(),
            whirlpool.getTickCurrentIndex(),
            whirlpool.getTickSpacing(),
            aToB);

    OrcTickArray[] tickArrays = new OrcTickArray[tickArrayAddresses.length];

    for (int i = 0; i < tickArrayAddresses.length; i++) {
      tickArrays[i] = fetchTickArray(tickArrayAddresses[i]);
    }

    return tickArrays;
  }

  /**
   * Extracts transfer fee information from token account data (if available).
   *
   * @param mintAddress The address of the token mint
   * @return TransferFee object if found, null otherwise
   */
  public OrcTransferFee extractTransferFee(String mintAddress) {
    try {
      PublicKey mintKey = PublicKey.fromBase58Encoded(mintAddress);
      var accountInfo = rpcClient.getAccountInfo(mintKey).join();

      if (accountInfo == null || accountInfo.data() == null) {
        return null;
      }

      byte[] data = (byte[]) accountInfo.data();

      // Check if this is a token-2022 account with extensions
      if (accountInfo.owner().equals(OrcConstants.TOKEN_2022_PROGRAM_ID.toBase58())) {
        // Simplified transfer fee extraction logic
        // This would need to be expanded for a real implementation
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Skip main account data
        buffer.position(82);

        // Loop through extensions
        while (buffer.hasRemaining()) {
          int extensionType = buffer.getInt() & 0x7FFFFFFF;
          int extensionSize = buffer.getInt();

          // Check for TransferFee extension (type 2)
          if (extensionType == 2 && extensionSize >= 10) {
            // offset + transferFeeConfigAuthority(option) + withdrawWithheldAuthority(option)
            buffer.position(buffer.position() + 8);

            // Read transfer fee config
            int feeBasisPoints = Short.toUnsignedInt(buffer.getShort());

            // Read maximum fee
            byte[] maxFeeBytes = new byte[8];
            buffer.get(maxFeeBytes);
            BigInteger maxFee = new BigInteger(1, maxFeeBytes);

            return OrcTransferFee.builder().feeBasisPoints(feeBasisPoints).maxFee(maxFee).build();
          } else {
            // Skip this extension
            buffer.position(buffer.position() + extensionSize - 8);
          }
        }
      }

      return null;
    } catch (Exception e) {
      log.error("Error extracting transfer fee: {}", e.getMessage(), e);
      return null;
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

  /**
   * Fetches and deserializes a Position account.
   *
   * @param positionAddress The address of the Position
   * @return The deserialized Position data
   * @throws Exception If there's an error fetching or parsing the account
   */
  public OrcPosition fetchPosition(String positionAddress) throws Exception {
    PublicKey positionKey = PublicKey.fromBase58Encoded(positionAddress);
    var accountInfo = rpcClient.getAccountInfo(positionKey).join();

    if (accountInfo == null) {
      throw new Exception("Position not found at address: " + positionAddress);
    }

    Object rawData = accountInfo.data();
    if (rawData == null) {
      throw new Exception("No account data found for position at address: " + positionAddress);
    }

    byte[] data = (byte[]) rawData;
    ByteBuffer buffer = ByteBuffer.wrap(data);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    OrcPosition position = new OrcPosition();
    position.setAddress(positionAddress);

    // Skip 8-byte discriminator
    buffer.position(8);

    // whirlpool - PublicKey (32 bytes)
    byte[] whirlpoolBytes = new byte[32];
    buffer.get(whirlpoolBytes);
    position.setWhirlpool(PublicKey.createPubKey(whirlpoolBytes).toBase58());

    // positionMint - PublicKey (32 bytes)
    byte[] positionMintBytes = new byte[32];
    buffer.get(positionMintBytes);
    position.setPositionMint(PublicKey.createPubKey(positionMintBytes).toBase58());

    // liquidity - u128 (16 bytes)
    byte[] liquidityBytes = new byte[16];
    buffer.get(liquidityBytes);
    position.setLiquidity(new BigInteger(1, liquidityBytes));

    // tickLowerIndex - i32 (4 bytes)
    position.setTickLowerIndex(buffer.getInt());

    // tickUpperIndex - i32 (4 bytes)
    position.setTickUpperIndex(buffer.getInt());

    // feeGrowthCheckpointA - u128 (16 bytes)
    buffer.position(buffer.position() + 16); // Skip fee growth checkpoint A

    // feeOwedA - u64 (8 bytes)
    byte[] feeOwedABytes = new byte[8];
    buffer.get(feeOwedABytes);
    position.setFeeOwedA(new BigInteger(1, feeOwedABytes));

    // feeGrowthCheckpointB - u128 (16 bytes)
    buffer.position(buffer.position() + 16); // Skip fee growth checkpoint B

    // feeOwedB - u64 (8 bytes)
    byte[] feeOwedBBytes = new byte[8];
    buffer.get(feeOwedBBytes);
    position.setFeeOwedB(new BigInteger(1, feeOwedBBytes));

    // Reward info (we'll skip for brevity)
    BigInteger[] rewardOwed = new BigInteger[3];
    for (int i = 0; i < 3; i++) {
      // Skip reward growth checkpoint - u128 (16 bytes)
      buffer.position(buffer.position() + 16);

      // Read reward owed - u64 (8 bytes)
      byte[] rewardOwedBytes = new byte[8];
      buffer.get(rewardOwedBytes);
      rewardOwed[i] = new BigInteger(1, rewardOwedBytes);
    }
    position.setRewardOwed(rewardOwed);

    return position;
  }
}
