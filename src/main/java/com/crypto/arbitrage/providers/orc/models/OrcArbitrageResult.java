package com.crypto.arbitrage.providers.orc.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of an arbitrage operation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcArbitrageResult {
  private boolean success;
  private String firstTransactionSignature;
  private String secondTransactionSignature;
  private String resultMessage;
}
