package com.crypto.arbitrage.providers.orc.models;

import com.crypto.arbitrage.providers.orc.constants.IncreaseLiquidityParamType;
import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a parameter for increasing liquidity. Contains both the parameter type and the
 * associated value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncreaseLiquidityParam {
  private IncreaseLiquidityParamType type;
  private BigInteger value;
}
