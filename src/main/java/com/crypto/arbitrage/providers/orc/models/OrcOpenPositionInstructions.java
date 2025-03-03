package com.crypto.arbitrage.providers.orc.models;

import java.math.BigInteger;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import software.sava.core.accounts.Signer;
import software.sava.core.tx.Instruction;

/**
 * Represents the result from opening a position with the required instructions, liquidity quote,
 * additional signers and initialization cost.
 */
@Data
@Builder
@AllArgsConstructor
public class OrcOpenPositionInstructions {
  private String positionMint;
  private OrcLiquidityQuote quote;
  private List<Instruction> instructions;
  private List<Signer> additionalSigners;
  private BigInteger initializationCost;
}
