package com.crypto.arbitrage.providers.mexc.test_controller;

import com.crypto.arbitrage.providers.mexc.MexcProvider;
import com.crypto.arbitrage.providers.mexc.model.order.MexcLoginData;
import com.crypto.arbitrage.providers.mexc.websocket.MexcWebSocketManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import velox.api.layer1.data.SubscribeInfo;
import velox.api.layer1.data.SubscribeInfoCrypto;

@RestController
@RequestMapping("api/v1/mexc/websocket")
@RequiredArgsConstructor
public class MexcWebsocketTestController {

  private static final String ACCOUNT_UPDATES_PATTERN = "spot@private\\.account\\.v3\\.api";
  private static final String ACCOUNT_DEALS_PATTERN = "^spot@private\\.deals\\.v3\\.api$";
  private static final String TRADE_STREAM_PATTERN = "spot@public\\.deals\\.v3\\.api@[A-Z]+";
  private static final String SPOT_ACCOUNT_ORDERS_PATTERN = "^spot@private\\.orders\\.v3\\.api$";
  private static final String DEPTH_STREAM_PATTERN =
      "spot@public\\.limit\\.depth\\.v3\\.api@[A-Z]+@[0-9]+";

  private static final String ACCOUNT_UPDATES_PATTERN_VIOLATION =
      "Updates subscription request match spot@private.account.v3.api";
  private static final String ACCOUNT_DEALS_PATTERN_VIOLATION =
      "Deals subscription request must match 'spot@private.deals.v3.api'";
  private static final String SPOT_ACCOUNT_ORDERS_PATTERN_VIOLATION =
      "Orders subscription request must match 'spot@private.orders.v3.api'";
  private static final String TRADE_STREAM_PATTERN_VIOLATION =
      "Trade stream request must match the pattern 'spot@public.deals.v3.api@<SYMBOL>' with <SYMBOL> in uppercase.";
  private static final String DEPTH_STREAM_PATTERN_VIOLATION =
      "Depth stream request must match the pattern 'spot@public.limit.depth.v3.api@<SYMBOL>@<level>' where <SYMBOL> is uppercase and <level> is numeric.";

  private static final String UNSUBSCRIBE = "/unsubscribe";
  private static final String OPEN_WEBSOCKET = "/open-websocket";
  private static final String CLOSE_WEBSOCKET = "/close-websocket";
  private static final String ACCOUNT_DEALS = "/subscription/account-deals";
  private static final String TRADE_STREAMS = "/subscription/trade-streams";
  private static final String ACCOUNT_ORDERS = "/subscription/account-orders";
  private static final String ACCOUNT_UPDATES = "/subscription/account-updates";
  private static final String PARTIAL_BOOK_DEPTH_STREAM = "/subscription/partial-book-depth-stream";

  private final MexcProvider mexcProvider;
  private final MexcWebSocketManager mexcWebSocketManager;

  @PostMapping(TRADE_STREAMS)
  public ResponseEntity<?> subscribeToTradeStream(
      @RequestParam String req, @RequestParam double pips, @RequestParam double sizeMultiplier) {
    SubscribeInfo subscribeInfo = new SubscribeInfoCrypto(req, null, null, pips, sizeMultiplier);

    mexcProvider.subscribe(subscribeInfo);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @PostMapping(UNSUBSCRIBE)
  public ResponseEntity<?> unsubscribe(@RequestParam String req) {
    mexcProvider.unsubscribe(req);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @PostMapping(PARTIAL_BOOK_DEPTH_STREAM)
  public ResponseEntity<?> subscribeToPartialBookDepthStream(
      @RequestParam String req, @RequestParam double pips, @RequestParam double sizeMultiplier) {
    SubscribeInfo subscribeInfo = new SubscribeInfoCrypto(req, null, null, pips, sizeMultiplier);
    mexcProvider.subscribe(subscribeInfo);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @PostMapping(OPEN_WEBSOCKET)
  public ResponseEntity<?> openWebSocket(@ModelAttribute MexcLoginData mexcLoginData) {
    mexcProvider.login(mexcLoginData);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @PostMapping(CLOSE_WEBSOCKET)
  public ResponseEntity<?> closeWebSocket() {
    mexcProvider.close();
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @PostMapping(ACCOUNT_ORDERS)
  public ResponseEntity<?> subscribeToOrders(@RequestParam String req) {
    mexcWebSocketManager.subscribeToTopic(req);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @PostMapping(ACCOUNT_DEALS)
  public ResponseEntity<?> subscribeToDeals(@RequestParam String req) {
    mexcWebSocketManager.subscribeToTopic(req);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @PostMapping(ACCOUNT_UPDATES)
  public ResponseEntity<?> subscribeToUpdates(@RequestParam String req) {
    if (!req.matches(ACCOUNT_UPDATES_PATTERN)) {
      return ResponseEntity.badRequest().body(ACCOUNT_UPDATES_PATTERN_VIOLATION);
    }
    mexcWebSocketManager.subscribeToTopic(req);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
