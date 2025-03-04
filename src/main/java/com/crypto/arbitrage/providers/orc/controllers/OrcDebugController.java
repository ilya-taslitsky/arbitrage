package com.crypto.arbitrage.providers.orc.controllers;

import com.crypto.arbitrage.providers.orc.constants.OrcConstants;
import com.crypto.arbitrage.providers.orc.models.OrcSwapQuote;
import com.crypto.arbitrage.providers.orc.services.OrcSwapService;
import com.crypto.arbitrage.providers.orc.services.OrcWhirlpoolService;
import com.crypto.arbitrage.providers.orc.utils.OrcDebugUtility;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for debugging Orca Whirlpool operations. These endpoints should only be enabled in
 * development/test environments.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orc/debug")
public class OrcDebugController {

  private final OrcDebugUtility debugUtility;
  private final OrcWhirlpoolService whirlpoolService;
  private final OrcSwapService swapService;

  /** Endpoint to diagnose a Whirlpool. */
  @GetMapping("/diagnose-pool")
  public ResponseEntity<String> diagnosePool(@RequestParam(required = false) String poolAddress) {

    String poolToUse = poolAddress != null ? poolAddress : OrcConstants.SOL_USDC_POOL;

    try {
      log.info("Diagnosing pool: {}", poolToUse);
      String diagnosticReport = debugUtility.diagnosePool(poolToUse);
      return ResponseEntity.ok(diagnosticReport);
    } catch (Exception e) {
      log.error("Error diagnosing pool: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error diagnosing pool: " + e.getMessage());
    }
  }

  /** Endpoint to analyze an error message. */
  @PostMapping("/analyze-error")
  public ResponseEntity<String> analyzeError(@RequestParam String errorMessage) {

    try {
      log.info("Analyzing error: {}", errorMessage);
      String analysis = debugUtility.analyzeError(errorMessage);
      return ResponseEntity.ok(analysis);
    } catch (Exception e) {
      log.error("Error analyzing error message: {}", e.getMessage(), e);
      return ResponseEntity.badRequest().body("Error analyzing error message: " + e.getMessage());
    }
  }

  /** Endpoint to test quote calculation for specific amounts. */
  @GetMapping("/test-quote")
  public ResponseEntity<Map<String, Object>> testQuoteCalculation(
      @RequestParam(required = false) String poolAddress,
      @RequestParam(required = false, defaultValue = "1000000") long amount,
      @RequestParam(required = false, defaultValue = "USDC") String tokenType,
      @RequestParam(required = false, defaultValue = "false") boolean aToB) {

    String poolToUse = poolAddress != null ? poolAddress : OrcConstants.SOL_USDC_POOL;
    String inputMint;

    // Determine input token mint based on tokenType parameter
    if ("SOL".equalsIgnoreCase(tokenType)) {
      inputMint = OrcConstants.WSOL_MINT;
    } else if ("USDC".equalsIgnoreCase(tokenType)) {
      inputMint = OrcConstants.USDC_MINT;
    } else if ("USDT".equalsIgnoreCase(tokenType)) {
      inputMint = OrcConstants.USDT_MINT;
    } else {
      inputMint = tokenType; // Assume the tokenType is an actual mint address
    }

    try {
      log.info(
          "Testing quote calculation for pool: {}, amount: {}, token: {}, aToB: {}",
          poolToUse,
          amount,
          tokenType,
          aToB);

      // Fetch the pool
      double price = whirlpoolService.getPoolPrice(poolToUse);

      // Calculate quote
      BigInteger amountBigInt = BigInteger.valueOf(amount);
      OrcSwapQuote quote = null;

      try {
        // Get the pool and calculate quote
        var pool = whirlpoolService.getPool(poolToUse);
        boolean isInputTokenA = pool.getTokenMintA().equals(inputMint);
        boolean direction = (isInputTokenA == aToB);

        quote = swapService.calculateExactInSwapQuote(amountBigInt, direction, 100, pool);
      } catch (Exception e) {
        log.error("Error calculating quote: {}", e.getMessage(), e);
      }

      // Build response
      Map<String, Object> response = new HashMap<>();
      response.put("pool", poolToUse);
      response.put("price", price);
      response.put("inputToken", tokenType);
      response.put("inputAmount", amount);
      response.put("direction", aToB ? "A to B" : "B to A");

      if (quote != null) {
        response.put("estimatedOutput", quote.getEstimatedAmountOut());
        response.put("minimumOutput", quote.getTokenMinOut());
        response.put("valid", !quote.getEstimatedAmountOut().equals(BigInteger.ZERO));
      } else {
        response.put("error", "Failed to calculate quote");
      }

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error testing quote calculation: {}", e.getMessage(), e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", e.getMessage());
      return ResponseEntity.badRequest().body(errorResponse);
    }
  }

  /** Endpoint to get minimum viable amounts for a pool. */
  @GetMapping("/minimum-amounts")
  public ResponseEntity<Map<String, Object>> getMinimumViableAmounts(
      @RequestParam(required = false) String poolAddress) {

    String poolToUse = poolAddress != null ? poolAddress : OrcConstants.SOL_USDC_POOL;

    try {
      log.info("Getting minimum viable amounts for pool: {}", poolToUse);

      // This is a static method we can use for demonstration
      Map<String, Object> response = new HashMap<>();
      response.put("pool", poolToUse);

      // These are the recommended minimum amounts for SOL-USDC pool
      if (poolToUse.equals(OrcConstants.SOL_USDC_POOL)) {
        response.put("minimumSOL", "0.01 SOL (10,000,000 lamports)");
        response.put("minimumUSDC", "1 USDC (1,000,000 units)");
        response.put("note", "These are conservative estimates to ensure successful swaps");
      } else {
        // For other pools, suggest viewing the full diagnostic report
        response.put(
            "note",
            "Call /diagnose-pool endpoint to see recommended minimum amounts for this pool");
      }

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error getting minimum viable amounts: {}", e.getMessage(), e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", e.getMessage());
      return ResponseEntity.badRequest().body(errorResponse);
    }
  }

  /** Endpoint to test various swap amounts to find viable minimums. */
  @GetMapping("/find-minimum-amount")
  public ResponseEntity<Map<String, Object>> findMinimumAmount(
      @RequestParam(required = false) String poolAddress,
      @RequestParam(required = false, defaultValue = "USDC") String tokenType) {

    String poolToUse = poolAddress != null ? poolAddress : OrcConstants.SOL_USDC_POOL;
    String inputMint;

    // Determine input token mint based on tokenType parameter
    if ("SOL".equalsIgnoreCase(tokenType)) {
      inputMint = OrcConstants.WSOL_MINT;
    } else if ("USDC".equalsIgnoreCase(tokenType)) {
      inputMint = OrcConstants.USDC_MINT;
    } else if ("USDT".equalsIgnoreCase(tokenType)) {
      inputMint = OrcConstants.USDT_MINT;
    } else {
      inputMint = tokenType; // Assume the tokenType is an actual mint address
    }

    try {
      log.info("Finding minimum amount for pool: {}, token: {}", poolToUse, tokenType);

      // Define amounts to test based on token type
      long[] amountsToTest;
      if ("SOL".equalsIgnoreCase(tokenType)) {
        // For SOL (9 decimals): test from 0.0001 SOL to 0.1 SOL
        amountsToTest =
            new long[] {
              100_000, // 0.0001 SOL
              1_000_000, // 0.001 SOL
              5_000_000, // 0.005 SOL
              10_000_000, // 0.01 SOL
              50_000_000, // 0.05 SOL
              100_000_000 // 0.1 SOL
            };
      } else {
        // For USDC/USDT (6 decimals): test from 0.01 USDC to 10 USDC
        amountsToTest =
            new long[] {
              10_000, // 0.01 USDC
              100_000, // 0.1 USDC
              500_000, // 0.5 USDC
              1_000_000, // 1 USDC
              5_000_000, // 5 USDC
              10_000_000 // 10 USDC
            };
      }

      // Get the pool
      var pool = whirlpoolService.getPool(poolToUse);
      boolean isInputTokenA = pool.getTokenMintA().equals(inputMint);

      // Test each amount and record results
      Map<String, Object> response = new HashMap<>();
      response.put("pool", poolToUse);
      response.put("token", tokenType);

      Map<String, Object> results = new HashMap<>();
      BigInteger firstValidAmount = null;

      for (long amount : amountsToTest) {
        BigInteger amountBigInt = BigInteger.valueOf(amount);
        boolean valid = false;
        BigInteger output = BigInteger.ZERO;

        try {
          // Calculate quote
          OrcSwapQuote quote =
              swapService.calculateExactInSwapQuote(amountBigInt, isInputTokenA, 100, pool);

          valid = !quote.getEstimatedAmountOut().equals(BigInteger.ZERO);
          output = quote.getEstimatedAmountOut();

          // Record first valid amount
          if (valid && firstValidAmount == null) {
            firstValidAmount = amountBigInt;
          }
        } catch (Exception e) {
          log.debug("Error calculating quote for amount {}: {}", amount, e.getMessage());
        }

        Map<String, Object> amountResult = new HashMap<>();
        amountResult.put("valid", valid);
        amountResult.put("output", output.toString());

        // Add formatted amount for display
        if ("SOL".equalsIgnoreCase(tokenType)) {
          amountResult.put("formatted", String.format("%.6f SOL", amount / 1_000_000_000.0));
        } else {
          amountResult.put("formatted", String.format("%.4f USDC", amount / 1_000_000.0));
        }

        results.put(String.valueOf(amount), amountResult);
      }

      response.put("results", results);

      if (firstValidAmount != null) {
        response.put("recommendedMinimum", firstValidAmount.toString());

        // Add formatted recommendation
        if ("SOL".equalsIgnoreCase(tokenType)) {
          response.put(
              "recommendedFormatted",
              String.format("%.6f SOL", firstValidAmount.doubleValue() / 1_000_000_000.0));
        } else {
          response.put(
              "recommendedFormatted",
              String.format("%.4f USDC", firstValidAmount.doubleValue() / 1_000_000.0));
        }
      } else {
        response.put("recommendedMinimum", "No valid amount found in test range");
      }

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Error finding minimum amount: {}", e.getMessage(), e);
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", e.getMessage());
      return ResponseEntity.badRequest().body(errorResponse);
    }
  }
}
