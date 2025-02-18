package com.crypto.arbitrage.providers.orc;

import com.crypto.arbitrage.providers.orc.model.ORCLoginData;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.stereotype.Component;
import velox.api.layer0.live.ExternalLiveBaseProvider;
import velox.api.layer1.data.LoginData;
import velox.api.layer1.data.OrderSendParameters;
import velox.api.layer1.data.OrderUpdateParameters;
import velox.api.layer1.data.SubscribeInfo;

@Slf4j
@Component
@RequiredArgsConstructor
public class ORCProvider extends ExternalLiveBaseProvider {

  public static final String NAME = "ORC";

  private RpcClient solanaClient;

  private final AtomicBoolean isLoggedIn = new AtomicBoolean(false);

  @Override
  public void login(LoginData loginData) {
    if (isLoggedIn.get()) {
      log.warn("Method login: Session is already active");
      return;
    }
    if (!(loginData instanceof ORCLoginData orcLoginData)) {
      log.error("Method login: LoginData must be of type ORCLoginData");
      return;
    }
    try {
      solanaClient = new RpcClient(orcLoginData.getClusterUrl());
      isLoggedIn.set(true);
      log.info("Method login: Connected to Solana cluster {}", orcLoginData.getClusterUrl());
    } catch (Exception e) {
      log.error("Method login: Error connecting to Solana cluster: {}", e.getMessage());
    }
  }

  @Override
  public String getSource() {
    return "ORC Provider";
  }

  @Override
  public void close() {
    solanaClient = null;
    isLoggedIn.set(false);
    log.info("Method close: Disconnected from Solana cluster.");
  }

  @Override
  public String formatPrice(String symbol, double price) {
    return String.format("%s: %.6f", symbol, price);
  }

  @Override
  public void subscribe(SubscribeInfo subscribeInfo) {
    // TODO
  }

  @Override
  public void unsubscribe(String symbol) {
    // TODO
  }

  @Override
  public void sendOrder(OrderSendParameters orderSendParameters) {
    // TODO
  }

  @Override
  public void updateOrder(OrderUpdateParameters orderUpdateParameters) {
    // TODO
  }
}
