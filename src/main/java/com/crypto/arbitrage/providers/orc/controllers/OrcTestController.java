package com.crypto.arbitrage.providers.orc.controllers;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.OrcArbitrageOpportunity;
import com.crypto.arbitrage.providers.orc.models.OrcArbitrageResult;
import com.crypto.arbitrage.providers.orc.models.OrcSwapTransactionResult;
import com.crypto.arbitrage.providers.orc.models.OrcSwapType;
import com.crypto.arbitrage.providers.orc.services.OrcWhirlpoolManagementService;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller for testing Orca Whirlpool operations. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orc/test")
public class OrcTestController {

  private final OrcWhirlpoolManagementService orcService;

  @GetMapping("/simple-swap")
  public ResponseEntity<OrcSwapTransactionResult> testSimpleSwap(
      @RequestParam(defaultValue = "1000000") long amount, // 1 USDC by default (6 decimals)
      @RequestParam(defaultValue = "true") boolean buyingSol) {

    try {
      log.info("Executing test swap: amount={}, buyingSol={}", amount, buyingSol);

      OrcSwapTransactionResult result;
      if (buyingSol) {
        // Buy SOL with USDC
        result = orcService.swapUsdcToSol(BigInteger.valueOf(amount), 100);
      } else {
        // Buy USDC with SOL
        result = orcService.swapSolToUsdc(BigInteger.valueOf(amount), 100);
      }

      return ResponseEntity.ok(result);
    } catch (Exception e) {
      log.error("Error executing test swap: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body(null);
    }
  }

  /** Custom swap endpoint with more control over parameters. */
  @GetMapping("/custom-swap")
  public ResponseEntity<OrcSwapTransactionResult> testCustomSwap(
      @RequestParam long amount,
      @RequestParam String inputMint,
      @RequestParam(required = false) String poolAddress,
      @RequestParam(defaultValue = "EXACT_IN") String swapType,
      @RequestParam(required = false) Integer slippageBps) {

    try {
      log.info(
          "Executing custom swap: amount={}, inputMint={}, poolAddress={}, swapType={}, slippageBps={}",
          amount,
          inputMint,
          poolAddress,
          swapType,
          slippageBps);

      OrcSwapType type = OrcSwapType.valueOf(swapType);

      OrcSwapTransactionResult result =
          orcService.swapTokens(
              BigInteger.valueOf(amount), inputMint, poolAddress, type, slippageBps);

      return ResponseEntity.ok(result);
    } catch (Exception e) {
      log.error("Error executing custom swap: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body(null);
    }
  }

  /** Endpoint to get the current price of a pool. */
  @GetMapping("/pool-price")
  public ResponseEntity<Map<String, Object>> getPoolPrice(
      @RequestParam(required = false) String poolAddress) {

    try {
      String pool = poolAddress != null ? poolAddress : OrcConstants.SOL_USDC_POOL;
      log.info("Getting price for pool: {}", pool);

      double price = orcService.getPoolPrice(pool);

      Map<String, Object> response = new HashMap<>();
      response.put("pool", pool);
      response.put("price", price);

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error getting pool price: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body(null);
    }
  }

  /** Endpoint to search for arbitrage opportunities. */
  @GetMapping("/find-arbitrage")
  public ResponseEntity<OrcArbitrageOpportunity> findArbitrageOpportunity(
      @RequestParam(required = false) List<String> poolAddresses,
      @RequestParam(required = false, defaultValue = "So11111111111111111111111111111111111111112")
          String inputTokenMint,
      @RequestParam(required = false, defaultValue = "1000000") long minAmount,
      @RequestParam(required = false, defaultValue = "10000000") long maxAmount,
      @RequestParam(required = false, defaultValue = "20") int minProfitBps) {

    try {
      // If no pools provided, use SOL-USDC and a few other known pools
      List<String> pools = poolAddresses;
      if (pools == null || pools.isEmpty()) {
        pools =
            Arrays.asList(
                OrcConstants.SOL_USDC_POOL,
                // Add other known pools here
                "HJPjoWUrhoZzkNfRpHuieeFk9WcZWjwy6PBjZ81ngndJ" // Example SOL-USDT pool
                );
      }

      log.info("Finding arbitrage opportunities across {} pools", pools.size());

      OrcArbitrageOpportunity opportunity =
          orcService.findArbitrageOpportunity(
              pools,
              inputTokenMint,
              BigInteger.valueOf(minAmount),
              BigInteger.valueOf(maxAmount),
              minProfitBps);

      if (opportunity != null) {
        return ResponseEntity.ok(opportunity);
      } else {
        return ResponseEntity.noContent().build();
      }
    } catch (Exception e) {
      log.error("Error finding arbitrage opportunities: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body(null);
    }
  }

  /** Endpoint to execute an arbitrage opportunity. */
  @PostMapping("/execute-arbitrage")
  public ResponseEntity<OrcArbitrageResult> executeArbitrage(
      @RequestParam String firstPoolAddress,
      @RequestParam String secondPoolAddress,
      @RequestParam String inputToken,
      @RequestParam long inputAmount) {

    try {
      log.info("Executing arbitrage between pools {} and {}", firstPoolAddress, secondPoolAddress);

      // Create a basic opportunity object
      OrcArbitrageOpportunity opportunity =
          OrcArbitrageOpportunity.builder()
              .firstPoolAddress(firstPoolAddress)
              .secondPoolAddress(secondPoolAddress)
              .inputToken(inputToken)
              .inputAmount(BigInteger.valueOf(inputAmount))
              .build();

      OrcArbitrageResult result = orcService.executeArbitrage(opportunity);

      return ResponseEntity.ok(result);
    } catch (Exception e) {
      log.error("Error executing arbitrage: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body(null);
    }
  }

  /** Endpoint to create a token account. */
  @PostMapping("/create-token-account")
  public ResponseEntity<Map<String, Object>> createTokenAccount(@RequestParam String mintAddress) {

    try {
      log.info("Creating token account for mint: {}", mintAddress);

      boolean success = orcService.createTokenAccount(mintAddress);

      Map<String, Object> response = new HashMap<>();
      response.put("mint", mintAddress);
      response.put("success", success);

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error creating token account: {}", e.getMessage(), e);
      Map<String, Object> response = new HashMap<>();
      response.put("success", false);
      response.put("error", e.getMessage());
      return ResponseEntity.badRequest().body(response);
    }
  }
}
