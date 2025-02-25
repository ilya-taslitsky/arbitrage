package com.crypto.arbitrage.providers.orc.model.transaction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class OrcSwapResult {
  private final long tokenA;
  private final long tokenB;
  private final long tradeFee;
}
