package com.crypto.arbitrage.service.bot;

import com.crypto.arbitrage.data.entity.Bot;
import com.crypto.arbitrage.service.datasource.cex.CEXEngine;
import com.crypto.arbitrage.service.datasource.dex.DEXProvider;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import velox.api.layer1.Layer1ApiProvider;

@Getter
@Component
@Scope("prototype")
public class BotLocal {
  @Setter private Bot bot;
  private final CEXEngine cexEngine;
  private final DEXProvider dexProvider;
  private final Layer1ApiProvider layer1ApiProvider;

  @Getter private final AtomicBoolean isLocalRunning = new AtomicBoolean(false);

  public BotLocal(
      CEXEngine cexEngine, DEXProvider dexProvider, Layer1ApiProvider layer1ApiProvider) {
    this.cexEngine = cexEngine;
    this.dexProvider = dexProvider;
    this.layer1ApiProvider = layer1ApiProvider;
  }

  public void start() {
    if (isLocalRunning.compareAndSet(false, true)) {
      // Initialize components
      // TODO initialize CEX
      dexProvider.initialize();
      // Additional initialization can be added here
    }
  }

  public void stop() {
    if (isLocalRunning.compareAndSet(true, false)) {
      // Cleanup
      // TODO cleanup CEX
      dexProvider.shutdown();
      // Additional cleanup can be added here
    }
  }

  public boolean isLocalRunning() {
    return isLocalRunning.get();
  }

  public String getDexTradingPair() {
    return bot.getDexPairId().getPairAsString();
  }

  public String getCexTradingPair() {
    return bot.getCexPairId().getPairAsString();
  }

  public double getSlippagePercent() {
    return bot.getSlippagePercent() != null ? bot.getSlippagePercent() : 0.5;
  }

  public boolean isActive() {
    return bot.getActive();
  }

  public boolean isRunning() {
    return bot.getRunning();
  }
}
