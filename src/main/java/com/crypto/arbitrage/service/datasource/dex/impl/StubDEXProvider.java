package com.crypto.arbitrage.service.datasource.dex.impl;

import com.crypto.arbitrage.service.datasource.dex.DEXProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
public class StubDEXProvider implements DEXProvider {
  private final Random random = new Random();
  private boolean initialized = false;

  @Override
  public double getPrice(String tradingPair, double amount) {
    if (!initialized) {
      throw new IllegalStateException("DEXProvider not initialized");
    }
    // Generate a random price between 0.95 and 1.05 of the amount
    return amount * (0.95 + random.nextDouble() * 0.1);
  }

  @Override
  public boolean executeSwap(String tradingPair, double amount, boolean isBuy) {
    if (!initialized) {
      throw new IllegalStateException("DEXProvider not initialized");
    }
    // Simulate 90% success rate for swaps
    return random.nextDouble() < 0.9;
  }

  @Override
  public void initialize() {
    log.info("Initializing StubDEXProvider");
    initialized = true;
  }

  @Override
  public void shutdown() {
    log.info("Shutting down StubDEXProvider");
    initialized = false;
  }
}
