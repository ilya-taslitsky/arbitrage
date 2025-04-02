package com.crypto.arbitrage.service.bot;

import com.crypto.arbitrage.dao.BotRepository;
import com.crypto.arbitrage.data.Direction;
import com.crypto.arbitrage.data.entity.Bot;
import com.crypto.arbitrage.data.entity.TransactionInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotManager {
  private final BotRepository botRepository;
  private final ApplicationContext applicationContext;
  private final Map<Long, BotLocal> botCache = new ConcurrentHashMap<>();
  private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

  @PostConstruct
  public void initialize() {
    try {
      botRepository.findAllByActiveTrue().forEach(this::registerBot);
    } catch (Exception e) {
      log.error("Error during bot initialization", e);
      throw new RuntimeException("Error during bot initialization", e);
    }
  }

  public void registerBot(Bot bot) {
    if (botCache.containsKey(bot.getId())) {
      log.warn("Bot with id {} is already registered", bot.getId());
      return;
    }

    BotLocal botLocal = applicationContext.getBean(BotLocal.class);
    botLocal.setBot(bot);
    botCache.put(botLocal.getBot().getId(), botLocal);

    if (bot.getActive() && bot.getRunning()) {
      startBot(bot.getId());
    }
    // TODO: if bot not running create and save signals
  }

  public void startBot(Long botId) {
    BotLocal botLocal = botCache.get(botId);
    if (botLocal == null) {
      log.error("Bot with id {} not found", botId);
      return;
    }

    botLocal.start();
    executorService.scheduleAtFixedRate(() -> executeBotLogic(botLocal), 0, 2, TimeUnit.SECONDS);
  }

  public void stopBot(Long botId) {
    BotLocal botLocal = botCache.get(botId);
    if (botLocal == null) {
      log.error("Bot with id {} not found", botId);
      return;
    }

    botLocal.stop();
  }

  private void executeBotLogic(BotLocal botLocal) {
    if (!botLocal.isLocalRunning()) {
      return;
    }

    try {
      // Get prices from both exchanges
      double dexPrice =
          botLocal
              .getDexProvider()
              .getPrice(
                  botLocal.getDexTradingPair(), botLocal.getBot().getTradingAmount().doubleValue());

      double cexPrice =
          botLocal.getCexEngine().getBidPrice()
              * botLocal.getBot().getTradingAmount().doubleValue();

      // Calculate price difference percentage
      double priceDiffPercent = Math.abs((dexPrice - cexPrice) / cexPrice * 100);

      // Check if arbitrage opportunity exists
      if (priceDiffPercent >= botLocal.getBot().getProfitPercent()) {
        // Create and save signal
        createArbitrageSignal(botLocal, dexPrice, cexPrice, priceDiffPercent);

        // Execute trades if bot is running
        if (botLocal.getBot().getRunning()) {
          executeArbitrageTrades(botLocal, dexPrice, cexPrice);
        }
      }
    } catch (Exception e) {
      log.error(
          "Error executing bot logic for bot {}: {}", botLocal.getBot().getId(), e.getMessage(), e);
    }
  }

  private void createArbitrageSignal(
      BotLocal botLocal, double dexPrice, double cexPrice, double priceDiffPercent) {
    // TODO: Implement signal creation and saving
    log.info(
        "Arbitrage signal detected for bot {}: DEX price: {}, CEX price: {}, Diff: {}%",
        botLocal.getBot().getId(), dexPrice, cexPrice, priceDiffPercent);
  }

  private void executeArbitrageTrades(BotLocal botLocal, double dexPrice, double cexPrice) {
    Bot bot = botLocal.getBot();
    double tradingAmount = bot.getTradingAmount().doubleValue();
    double slippagePercent = botLocal.getSlippagePercent();

    // Determine which exchange has the better price
    boolean isDexPriceBetter = dexPrice > cexPrice;
    Direction dexDirection = isDexPriceBetter ? Direction.SELL : Direction.BUY;
    Direction cexDirection = isDexPriceBetter ? Direction.BUY : Direction.SELL;

    // Execute DEX swap first
    boolean dexSuccess =
        botLocal
            .getDexProvider()
            .executeSwap(
                botLocal.getDexTradingPair(), tradingAmount, dexDirection == Direction.BUY);

    if (dexSuccess) {
      // Execute CEX trade
      // TODO: Implement CEX trade execution using CEXEngine
      // For now, we'll just log the success
      log.info("DEX swap successful for bot {}, executing CEX trade", bot.getId());

      // Create and save transaction info
      TransactionInfo transactionInfo = new TransactionInfo();
      transactionInfo.setCexPair(botLocal.getCexTradingPair());
      transactionInfo.setDexPair(botLocal.getDexTradingPair());
      transactionInfo.setDirectionDex(dexDirection);
      transactionInfo.setBlockchain(bot.getBlockchain().getName());
      transactionInfo.setSumBought(BigDecimal.valueOf(isDexPriceBetter ? cexPrice : dexPrice));
      transactionInfo.setSumSold(BigDecimal.valueOf(isDexPriceBetter ? dexPrice : cexPrice));
      transactionInfo.setProfit(BigDecimal.valueOf(Math.abs(dexPrice - cexPrice)));
      transactionInfo.setProfitPercent(Math.abs((dexPrice - cexPrice) / cexPrice * 100));
      transactionInfo.setDate(LocalDateTime.now());
      // TODO: Set actual fees
      transactionInfo.setCexFee(0.1); // Example fee
      transactionInfo.setDexFee(0.1); // Example fee

      // TODO: Save transaction info to database
      log.info("Transaction info created: {}", transactionInfo);
    } else {
      log.error("DEX swap failed for bot {}", bot.getId());
    }
  }

  @PreDestroy
  public void shutdown() {
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
