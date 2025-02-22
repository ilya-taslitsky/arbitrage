package com.crypto.arbitrage.providers.orc.test_controller;

import com.crypto.arbitrage.providers.orc.OrcProvider;
import com.crypto.arbitrage.providers.orc.model.OrcAllPoolsInfoResp;
import com.crypto.arbitrage.providers.orc.model.OrcPoolInfoResp;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orc")
public class OrcTestController {

  private static final String VERSION_URL = "/account";
  private static final String ALL_POOLS_INFO = "/pools/all";
  private static final String POOL_INFO = "/pools/{publicKey}";
  private static final String SIMULATE_TRANSACTION = "/simulate-transaction";

  private final OrcProvider orcProvider;

  @GetMapping(VERSION_URL)
  public void getAccountInfo(@RequestParam String req) {
    orcProvider.getAccountInfoViaSolana(req);
  }

  @GetMapping(ALL_POOLS_INFO)
  public OrcAllPoolsInfoResp getAllPoolsInfo() {
    return orcProvider.getAllPoolsInfoViaOrcApi();
  }

  @GetMapping(POOL_INFO)
  public OrcPoolInfoResp getPoolInfo(@PathVariable String publicKey) {
    return orcProvider.getPoolInfoViaOrcApi(publicKey);
  }

  @GetMapping(SIMULATE_TRANSACTION)
  public String simulateTransaction(@RequestParam String req) throws Exception {
    return orcProvider.executeSellSolTransaction();
  }
}
