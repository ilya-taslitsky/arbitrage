package com.crypto.arbitrage.service.datasource.dex;

public interface DEXProvider {
  double getPrice(String tradingPair, double amount);

  boolean executeSwap(String tradingPair, double amount, boolean isBuy);

  void initialize();

  void shutdown();
}
