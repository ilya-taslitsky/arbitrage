package com.crypto.arbitrage.providers.orc;

import com.crypto.arbitrage.providers.orc.model.OrcAllPoolsInfoResp;
import com.crypto.arbitrage.providers.orc.model.OrcPoolInfoResp;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.rpc.json.http.client.SolanaRpcClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrcProvider {
  private static final String GET_POOL = "/pools/";
  private static final String GET_ALL_POOLS_INFO_URL = "/pools/all";
  private static final String ORC_API_URL = "https://api.orca.so/v2/solana";

  private final RestClient restClient;
  private final SolanaRpcClient rpcClient;

  public void getAccountInfo(@NonNull String publicKey) {
    var accountInfo =
        rpcClient
            .getAccountInfo(PublicKey.fromBase58Encoded(publicKey), AddressLookupTable.FACTORY)
            .join();
    log.info(accountInfo.toString());
  }

  public OrcAllPoolsInfoResp getAllPoolsInfo() {
    try {
      OrcAllPoolsInfoResp orcAllPoolsInfoResp =
          restClient
              .get()
              .uri(ORC_API_URL + GET_ALL_POOLS_INFO_URL)
              .header("Content-Type", "application/json")
              .retrieve()
              .body(OrcAllPoolsInfoResp.class);
      if (orcAllPoolsInfoResp != null) {
        log.info(orcAllPoolsInfoResp.toString());
      } else {
        log.info("Method getAllPoolsInfo: response is null");
      }
      return orcAllPoolsInfoResp;
    } catch (Exception e) {
      log.error(e.getMessage());
    }
    return null;
  }

  public OrcPoolInfoResp getPoolInfo(@NonNull String publicKey) {
    try {
      OrcPoolInfoResp orcPoolInfoResp =
          restClient
              .get()
              .uri(ORC_API_URL + GET_POOL + publicKey)
              .header("Content-Type", "application/json")
              .retrieve()
              .body(OrcPoolInfoResp.class);
      if (orcPoolInfoResp != null) {
        log.info(orcPoolInfoResp.toString());
      } else {
        log.info("Method getPoolInfo: response is null");
      }
      return orcPoolInfoResp;
    } catch (HttpClientErrorException e) {
      log.error(e.getMessage());
    }
    return null;
  }
}
