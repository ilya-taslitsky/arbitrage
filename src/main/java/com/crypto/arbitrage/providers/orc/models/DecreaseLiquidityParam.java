package com.crypto.arbitrage.providers.orc.models;

import com.crypto.arbitrage.providers.orc.constants.DecreaseLiquidityParamType;
import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing a decrease liquidity parameter. It contains both a type and the associated
 * BigInteger value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecreaseLiquidityParam {
  private DecreaseLiquidityParamType type;
  private BigInteger value;
}
