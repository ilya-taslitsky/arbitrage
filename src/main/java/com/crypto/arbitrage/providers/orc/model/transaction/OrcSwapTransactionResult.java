package com.crypto.arbitrage.providers.orc.model.transaction;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.sava.core.tx.Instruction;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrcSwapTransactionResult {
  private List<Instruction> instructions;
  private String transactionSignature;
  private long tokenEstOut;
}
