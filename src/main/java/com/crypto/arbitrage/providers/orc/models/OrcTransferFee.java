package com.crypto.arbitrage.providers.orc.models;

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents transfer fee configuration for token-2022 tokens. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcTransferFee {
  private int feeBasisPoints; // Fee in basis points (1/100 of 1%)
  private BigInteger maxFee; // Maximum fee amount
}
