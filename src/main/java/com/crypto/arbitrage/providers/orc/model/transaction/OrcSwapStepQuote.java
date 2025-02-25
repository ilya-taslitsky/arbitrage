package com.crypto.arbitrage.providers.orc.model.transaction;

import java.math.BigInteger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class OrcSwapStepQuote {
  private final long amountIn;
  private final long amountOut;
  private final BigInteger nextSqrtPrice;
  private final long feeAmount;
}
