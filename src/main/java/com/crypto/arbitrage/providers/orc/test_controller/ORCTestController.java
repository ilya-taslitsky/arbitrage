package com.crypto.arbitrage.providers.orc.test_controller;

import com.crypto.arbitrage.providers.orc.ORCProvider;
import com.crypto.arbitrage.providers.orc.model.ORCLoginData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orc")
@RequiredArgsConstructor
public class ORCTestController {

  private static final String CONNECT_URL = "/connect";
  private static final String ORDER_URL = "/order";
  private static final String WITHDRAW_URL = "/withdraw";

  private final ORCProvider orcProvider;

  @PostMapping(CONNECT_URL)
  public ORCLoginData login(@RequestParam String req) {
    return ORCLoginData.builder().clusterUrl(req).build();
  }
}
