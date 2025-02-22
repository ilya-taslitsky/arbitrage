package com.crypto.arbitrage.providers.mexc.websocket;

import com.crypto.arbitrage.providers.mexc.common.MexcSignatureUtil;
import com.crypto.arbitrage.providers.mexc.model.event.MexcSubscriptionEvent;
import com.crypto.arbitrage.providers.mexc.model.order.MexcLoginData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class MexcWebSocketStateService {

  private final int MAX_PING_FAILURES = 3;
  private static final long PING_INTERVAL = 30;
  private static final long KEEPALIVE_INTERVAL = 30 * 60; // 30 minutes in seconds
  private static final long RECONNECT_DELAY_SECONDS = 10;
  private static final long HEARTBEAT_TIMEOUT_MS = 60000;
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String MEXC_API_KEY_HEADER = "X-MEXC-APIKEY";
  private static final String PING_MESSAGE = "{\"method\":\"PING\"}";
  private static final String USER_DATA_STREAM_URL = "/api/v3/userDataStream?";
  private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
  private static final String SUBSCRIPTION_TEMPLATE =
      "{\"method\":\"SUBSCRIPTION\",\"params\":[\"%s\"]}";
  private static final String UNSUBSCRIPTION_TEMPLATE =
      "{\"method\":\"UNSUBSCRIPTION\",\"params\":[\"%s\"]}";

  private final String apiUrl;
  private final RestClient restClient;
  private final String webSocketBaseUrl;
  private final ObjectMapper objectMapper;
  private ScheduledExecutorService pingExecutor;
  private final AtomicInteger reconnectAttempts;
  private final Object executorLock = new Object();
  private final MexcWebSocketClient webSocketClient;
  private ScheduledExecutorService keepaliveExecutor;
  private ScheduledExecutorService heartbeatExecutor;
  private final ScheduledExecutorService reconnectExecutor;
  private final AtomicInteger pingFailureCounter = new AtomicInteger(0);
  private final AtomicLong lastPongTime = new AtomicLong(System.currentTimeMillis());
  private final AtomicBoolean reconnectInProgress = new AtomicBoolean(false);
  private final ConcurrentSkipListSet<String> subscriptions = new ConcurrentSkipListSet<>();
  @Getter @Setter private AtomicBoolean isSessionEnabled = new AtomicBoolean(false);

  @Getter private String listenKey;
  @Setter private MexcLoginData loginData;

  public MexcWebSocketStateService(
      @Value("${mexc.api.url}") String apiUrl,
      @Value("${mexc.api.websocketBaseUrl}") String webSocketBaseUrl,
      MexcWebSocketClient webSocketClient,
      RestClient restClient,
      ObjectMapper objectMapper) {
    this.apiUrl = apiUrl;
    this.objectMapper = objectMapper;
    this.webSocketClient = webSocketClient;
    this.webSocketBaseUrl = webSocketBaseUrl;
    this.restClient = restClient;
    this.reconnectAttempts = new AtomicInteger(0);
    this.pingExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "PingExecutor"));
    this.keepaliveExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "KeepaliveExecutor"));
    this.heartbeatExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "HeartbeatExecutor"));
    this.reconnectExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "ReconnectExecutor"));
  }

  public String getListenKeyFromMexc(@NonNull MexcLoginData loginData) {
    if (this.loginData == null) {
      this.loginData = loginData;
    }
    Map<String, String> params = new LinkedHashMap<>();
    String signedUrl = getSignedUrl(params, loginData);
    try {
      ResponseEntity<String> response =
          restClient
              .post()
              .uri(signedUrl)
              .header(MEXC_API_KEY_HEADER, loginData.getApiKey())
              .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_APPLICATION_JSON)
              .retrieve()
              .toEntity(String.class);

      if (response.getStatusCode().value() == 200) {
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        String newListenKey = jsonNode.get("listenKey").asText();
        this.listenKey = newListenKey;
        log.info("Obtained new listenKey: {}", newListenKey);
        return newListenKey;
      } else {
        log.error(
            "Failed to create MexcWebSocket stream, status code: {}, body: {}",
            response.getStatusCode(),
            response.getBody());
      }
    } catch (IOException e) {
      log.error("Error creating MexcWebSocket stream: {}", e.getMessage());
      Thread.currentThread().interrupt();
    }
    return null;
  }

  public void onOpen() {
    reconnectAttempts.set(0);
    startPingExecutor();
    startKeepaliveExecutor();
    startHeartbeatMonitor();
  }

  public void onClose(CloseReason closeReason) {
    if (closeReason != null) {
      CloseReason.CloseCode closeCode = closeReason.getCloseCode();
      log.info("Method onClose: MexcWebSocket connection closed with reason: {}", closeCode);
      if (!isSessionEnabled.get()) {
        subscriptions.clear();
      }
      shutdownAndAwaitTerminationExecutors();
      if (!isSessionEnabled.get()) {
        scheduleReconnect();
      }
    } else {
      log.info("Method onClose: MexcWebSocket connection closed, no close reason provided.");
    }
  }

  public void onError() {
    scheduleReconnect();
  }

  private void startKeepaliveExecutor() {
    if (!isSessionOpen()) {
      log.error("Method startKeepaliveExecutor: User MexcWebSocket session is not open.");
      return;
    }

    keepaliveExecutor.scheduleAtFixedRate(
        () -> {
          try {
            keepAlive();
          } catch (Exception e) {
            log.error(
                "Method keepAlive: Error during keepalive MexcWebSocket listenKey: {}",
                e.getMessage());
          }
        },
        KEEPALIVE_INTERVAL,
        KEEPALIVE_INTERVAL,
        TimeUnit.SECONDS);
  }

  private void keepAlive() {
    if (loginData == null) {
      log.error("Method keepAlive: MexcLoginData is null.");
      return;
    }
    Map<String, String> params = new LinkedHashMap<>();
    params.put("listenKey", listenKey);
    String signedUrl = getSignedUrl(params, loginData);
    ResponseEntity<String> response =
        restClient
            .put()
            .uri(signedUrl)
            .header(MEXC_API_KEY_HEADER, loginData.getApiKey())
            .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_APPLICATION_JSON)
            .retrieve()
            .toEntity(String.class);

    log.info("Keepalive response code: {}", response.getStatusCode());
    log.info("Keepalive response body: {}", response.getBody());
  }

  private String getSignedUrl(
      @NonNull Map<String, String> params, @NonNull MexcLoginData loginData) {
    params.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));
    String rawQueryString =
        params.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
    String signature = MexcSignatureUtil.createSignature(loginData.getApiSecret(), rawQueryString);
    params.put("signature", signature);
    String encodedQueryString =
        params.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    return apiUrl + USER_DATA_STREAM_URL + encodedQueryString;
  }

  private void scheduleReconnect() {
    // Only schedule if not already in progress
    if (!reconnectInProgress.compareAndSet(false, true)) {
      return;
    }
    log.info("Scheduling MexcWebSocket reconnection in {} seconds", RECONNECT_DELAY_SECONDS);
    reconnectExecutor.schedule(this::attemptReconnect, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
  }

  private void attemptReconnect() {
    try {
      if (isSessionOpen()) {
        log.info("Reconnection: Existing session is still open, disconnecting...");
        webSocketClient.closeSessionOnReconnect();
      }
      // Clear session reference
      webSocketClient.getSession().set(null);

      // Renew the listenKey
      String newListenKey = getListenKeyFromMexc(loginData);
      if (newListenKey != null) {
        log.info("Renewed listenKey: {}", newListenKey);
        String newWsUrl = buildWebSocketUrlWithListenKey(webSocketBaseUrl);
        log.info("New MexcWebSocket URL: {}", newWsUrl);
        webSocketClient.setWebSocketUrlWithListenKey(newWsUrl);
        resetExecutors();
        pingFailureCounter.set(0);
        webSocketClient.connect();
        reconnectExecutor.schedule(
            () -> {
              if (isSessionOpen()) {
                subscriptions.forEach(this::subscribeToChannel);
                reconnectInProgress.set(false);
                reconnectAttempts.set(0);
              } else {
                log.error("Reconnection attempt did not result in an open session.");
                reconnectInProgress.set(false);
                scheduleReconnect(); // schedule another reconnect if needed
              }
            },
            2,
            TimeUnit.SECONDS);
      } else {
        log.error("Failed to renew listenKey; will retry reconnection.");
        reconnectInProgress.set(false);
        scheduleReconnect();
      }
    } catch (Exception e) {
      log.error(
          "Method attemptReconnect: WebSocket reconnection failed: Message: {} Cause: {} ",
          e.getMessage(),
          e.getCause().toString());
      reconnectInProgress.set(false);
      scheduleReconnect();
    }
  }

  public String buildWebSocketUrlWithListenKey(@NonNull String baseUrl) {
    return baseUrl + "?listenKey=" + listenKey;
  }

  public void subscribeToChannel(@NonNull String channel) {
    if (!isSessionOpen()) {
      log.error("Method subscribeToChannel: MexcWebSocket Session is not available or not open.");
    } else {
      subscriptions.add(channel);
      String message = String.format(SUBSCRIPTION_TEMPLATE, channel);
      webSocketClient.sendMessage(message);
      log.info("Sent subscription message to MexcWebSocket: {}", message);
    }
  }

  public void unsubscribeFromChannel(@NonNull String channel) {
    subscriptions.remove(channel);
    if (!isSessionOpen()) {
      log.error(
          "Method unsubscribeFromChannel: MexcWebSocket Session is not available or not open.");
    } else {
      String message = String.format(UNSUBSCRIPTION_TEMPLATE, channel);
      webSocketClient.sendMessage(message);
      log.info("Sent unsubscribe message to MexcWebSocket: {}", message);
    }
  }

  private void startPingExecutor() {
    if (!isSessionOpen()) {
      log.error("Method startPingExecutor: MexcWebSocket Session is not available or not open.");
      return;
    }
    pingExecutor.scheduleAtFixedRate(
        () -> {
          try {
            if (isSessionOpen()) {
              sendPing();
            } else {
              scheduleReconnect();
            }
          } catch (Exception e) {
            log.error("Method sendPing: Error sending ping to MexcWebSocket: {}", e.getMessage());
          }
        },
        PING_INTERVAL,
        PING_INTERVAL,
        TimeUnit.SECONDS);
  }

  private void sendPing() {
    Session currentSession = webSocketClient.getSession().get();
    if (!isSessionOpen()) {
      log.error("Method sendPing: Session is not available or not open.");
      scheduleReconnect();
    } else {
      String pingMessage = createPingMessage();
      currentSession
          .getAsyncRemote()
          .sendText(
              pingMessage,
              result -> {
                if (!result.isOK()) {
                  int failures = pingFailureCounter.incrementAndGet();
                  log.error(
                      "Ping failed ({} consecutive failures): {}",
                      failures,
                      result.getException().getMessage());
                  if (failures >= MAX_PING_FAILURES) {
                    scheduleReconnect();
                  }
                } else {
                  pingFailureCounter.set(0);
                }
              });
    }
  }

  public void onPongReceived() {
    lastPongTime.set(System.currentTimeMillis());
  }

  private void startHeartbeatMonitor() {
    heartbeatExecutor.scheduleAtFixedRate(
        () -> {
          long elapsed = System.currentTimeMillis() - lastPongTime.get();
          if (elapsed > HEARTBEAT_TIMEOUT_MS) {
            log.error("Heartbeat timeout: No PONG received in {} ms", elapsed);
            scheduleReconnect();
          }
        },
        HEARTBEAT_TIMEOUT_MS,
        HEARTBEAT_TIMEOUT_MS,
        TimeUnit.MILLISECONDS);
  }

  private String createPingMessage() {
    return String.format(PING_MESSAGE);
  }

  private void resetExecutors() {
    shutdownAndAwaitTerminationExecutors();
    // Reinitialize executors
    pingExecutor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "PingExecutor"));
    keepaliveExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "KeepaliveExecutor"));
    heartbeatExecutor =
        Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "HeartbeatExecutor"));
  }

  private void shutdownAndAwaitTermination(@NonNull ExecutorService executor) {
    if (!executor.isShutdown()) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
          executor.shutdownNow();
          if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            log.error("Executor did not terminate");
          }
        }
      } catch (InterruptedException ex) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  private void shutdownAndAwaitTerminationExecutors() {
    synchronized (executorLock) {
      shutdownAndAwaitTermination(pingExecutor);
      shutdownAndAwaitTermination(keepaliveExecutor);
      shutdownAndAwaitTermination(heartbeatExecutor);
    }
  }

  private boolean isSessionOpen() {
    Session session = webSocketClient.getSession().get();
    return session != null && session.isOpen();
  }

  /**
   * This method add channel to subscriptions set if subscription was confirmed from Mex WebSocket
   */
  @EventListener
  public void onSubscriptionEvent(@NonNull MexcSubscriptionEvent event) {
    subscriptions.add(event.getChannel());
    isSessionEnabled.compareAndExchange(false, true);
  }

  @PreDestroy
  public void onShutdown() {
    shutdownAndAwaitTerminationExecutors();
    shutdownAndAwaitTermination(reconnectExecutor);
  }
}
