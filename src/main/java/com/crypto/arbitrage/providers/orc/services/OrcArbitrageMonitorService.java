package com.crypto.arbitrage.providers.orc.services;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.OrcArbitrageOpportunity;
import com.crypto.arbitrage.providers.orc.models.OrcArbitrageResult;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrcArbitrageMonitorService {
  private final OrcWhirlpoolService whirlpoolService;
  private final List<String> monitoredPools;
  private final int profitThresholdBps = 20; // 0.2% minimum profit

  @Scheduled(fixedRate = 10000) // Run every 10 seconds
  public void monitorArbitrageOpportunities() {
    try {
      log.info("Monitoring arbitrage opportunities...");

      // Find opportunities using SOL as base token
      OrcArbitrageOpportunity solOpp = whirlpoolService.findArbitrageOpportunity(
              monitoredPools,
              OrcConstants.WSOL_MINT,
              BigInteger.valueOf(1_000_000), // 0.001 SOL
              BigInteger.valueOf(1_000_000_000), // 1 SOL
              profitThresholdBps);

      // Find opportunities using USDC as base token
      OrcArbitrageOpportunity usdcOpp = whirlpoolService.findArbitrageOpportunity(
              monitoredPools,
              OrcConstants.USDC_MINT,
              BigInteger.valueOf(1_000_000), // 1 USDC
              BigInteger.valueOf(1_000_000_000), // 1000 USDC
              profitThresholdBps);

      // Execute the more profitable opportunity if any
      if (solOpp != null && usdcOpp != null) {
        if (solOpp.getProfitBasisPoints() > usdcOpp.getProfitBasisPoints()) {
          executeAndLogArbitrage(solOpp);
        } else {
          executeAndLogArbitrage(usdcOpp);
        }
      } else if (solOpp != null) {
        executeAndLogArbitrage(solOpp);
      } else if (usdcOpp != null) {
        executeAndLogArbitrage(usdcOpp);
      } else {
        log.info("No profitable arbitrage opportunities found");
      }
    } catch (Exception e) {
      log.error("Error monitoring arbitrage: {}", e.getMessage(), e);
    }
  }

  private void executeAndLogArbitrage(OrcArbitrageOpportunity opportunity) {
    log.info("Executing arbitrage: {}% profit on {} tokens",
            opportunity.getProfitBasisPoints() / 100.0,
            opportunity.getInputAmount());

    OrcArbitrageResult result = whirlpoolService.executeArbitrage(opportunity);

    if (result.isSuccess()) {
      log.info("Arbitrage successful: {}", result.getResultMessage());
    } else {
      log.error("Arbitrage failed: {}", result.getResultMessage());
    }
  }
}
