package com.crypto.arbitrage.providers.orc.constants;

import java.math.BigInteger;
import software.sava.core.accounts.PublicKey;

/**
 * Constants used throughout the Orca Whirlpool SDK. This centralizes all constants to ensure
 * consistency.
 */
public class OrcConstants {
  // Solana Program IDs
  public static final String ORCA_WHIRLPOOL_PROGRAM_ID =
      "whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc";
  public static final PublicKey WHIRLPOOL_PROGRAM_ID =
      PublicKey.fromBase58Encoded(ORCA_WHIRLPOOL_PROGRAM_ID);
  public static final String MY_WALLET_ADDRESS = "BYcFJHa3pb5WLvr3rbu8KsFXeJgGeuxnhRRXKWfzz887";
  public static final String MY_WALLET_PRIVATE_KEY =
      "4BTSmN1jnmsMDtvFSjzfTK2pkhexAXcQuWxeKQe83LXvDKtb8KjnY3PE9sUBhqzpS2vUyKCtkLAsQvsPcJpAnWrj";
  public static final String SOL_USDC_POOL = "7qbRF6YsyGuLUVs6Y1q64bdVrfe4ZcUUz1JRdoVNUJnm";
  public static final PublicKey TOKEN_PROGRAM_ID =
      PublicKey.fromBase58Encoded("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
  public static final PublicKey TOKEN_2022_PROGRAM_ID =
      PublicKey.fromBase58Encoded("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb");
  public static final PublicKey SYSTEM_PROGRAM_ID =
      PublicKey.fromBase58Encoded("11111111111111111111111111111111");
  public static final PublicKey ASSOCIATED_TOKEN_PROGRAM_ID =
      PublicKey.fromBase58Encoded("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL");
  public static final PublicKey MEMO_PROGRAM_ID =
      PublicKey.fromBase58Encoded("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr");
  public static final PublicKey RENT_SYSVAR_ID =
      PublicKey.fromBase58Encoded("SysvarRent111111111111111111111111111111111");

  // Common Token Mints
  public static final String WSOL_MINT = "So11111111111111111111111111111111111111112";
  public static final String USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";
  public static final String USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB";

  // Whirlpool-specific Constants
  public static final int TICK_ARRAY_SIZE = 88;
  public static final int DEFAULT_SLIPPAGE_TOLERANCE_BPS = 100; // 1%
  public static final int MINIMUM_ARBITRAGE_PROFIT_BPS = 20; // 0.2%
  public static final int SPLASH_POOL_TICK_SPACING = 32896;

  // Math Constants (Q64.64 fixed-point)
  public static final BigInteger Q64 = BigInteger.ONE.shiftLeft(64);
  public static final BigInteger MAX_SQRT_PRICE = new BigInteger("79226673515401279992447579055");
  public static final BigInteger MIN_SQRT_PRICE = new BigInteger("4295048016");

  // Instruction Discriminators
  public static final byte SWAP_V2_INSTRUCTION = 4;
  public static final byte INCREASE_LIQUIDITY_INSTRUCTION = 19;
  public static final byte DECREASE_LIQUIDITY_INSTRUCTION = 20;
  public static final byte COLLECT_FEES_INSTRUCTION = 21;

  // Transaction Configuration
  public static final BigInteger DEFAULT_GAS_ESTIMATE = BigInteger.valueOf(5000); // 0.000005 SOL
}
