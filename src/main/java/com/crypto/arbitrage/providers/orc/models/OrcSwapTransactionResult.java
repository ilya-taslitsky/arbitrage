package com.crypto.arbitrage.providers.orc.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.sava.core.tx.Instruction;

/** Result of a swap transaction. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcSwapTransactionResult {
  private List<Instruction> instructions;
  private String transactionSignature;
  private long tokenEstOut;
  private boolean success;
  private String errorMessage;
}
