package com.crypto.arbitrage.providers.orc.model.transaction;

import java.math.BigInteger;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrcSwapQuote {
  public BigInteger tokenIn;
  public BigInteger tokenMaxIn;
  public BigInteger tokenMinOut;
  public BigInteger estimatedAmountIn;
  public BigInteger estimatedAmountOut;
  public BigInteger liquidityDelta;
}
