package com.crypto.arbitrage.providers.mexc.websocket;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import jakarta.websocket.Session;
import jakarta.annotation.PreDestroy;
import jakarta.websocket.CloseReason;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import com.crypto.arbitrage.providers.mexc.config.MexcConfig;
import com.crypto.arbitrage.providers.mexc.common.SignatureUtil;
import com.crypto.arbitrage.providers.mexc.model.order.MexcLoginData;

import java.util.Map;
import java.time.Instant;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Service
public class MexcWebSocketStateService {

    private final int MAX_PING_FAILURES = 3;
    private static final long PING_INTERVAL = 30;
    private static final long KEEPALIVE_INTERVAL = 30 * 60; // 30 minutes in seconds
    private static final long HEARTBEAT_TIMEOUT_MS = 60000;
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String MEXC_API_KEY_HEADER = "X-MEXC-APIKEY";
    private static final String PING_MESSAGE = "{\"method\":\"PING\"}";
    private static final String USER_DATA_STREAM_URL = "/api/v3/userDataStream?";
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private static final String SUBSCRIPTION_TEMPLATE = "{\"method\":\"SUBSCRIPTION\",\"params\":[\"%s\"]}";
    private static final String UNSUBSCRIPTION_TEMPLATE = "{\"method\":\"UNSUBSCRIPTION\",\"params\":[\"%s\"]}";

    private final String apiUrl;
    private final MexcConfig mexcConfig;
    private final RestClient restClient;
    private final String webSocketBaseUrl;
    private final ObjectMapper objectMapper;
    private ScheduledExecutorService pingExecutor;
    private final AtomicInteger reconnectAttempts;
    private final MexcWebSocketClient webSocketClient;
    private ScheduledExecutorService keepaliveExecutor;
    private ScheduledExecutorService heartbeatExecutor;
    private final ScheduledExecutorService reconnectExecutor;
    private final AtomicInteger pingFailureCounter = new AtomicInteger(0);
    private final AtomicLong lastPongTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean reconnectInProgress = new AtomicBoolean(false);
    @Getter
    private String listenKey;

    public MexcWebSocketStateService(@Value("${mexc.api.url}") String apiUrl, MexcConfig mexcConfig,
                                     @Value("${mexc.api.websocketBaseUrl}") String webSocketBaseUrl,
                                     MexcWebSocketClient webSocketClient,
                                     RestClient restClient,
                                     ObjectMapper objectMapper) {
        this.apiUrl = apiUrl;
        this.mexcConfig = mexcConfig;
        this.objectMapper = objectMapper;
        this.webSocketClient = webSocketClient;
        this.webSocketBaseUrl = webSocketBaseUrl;
        this.restClient = restClient;
        this.reconnectAttempts = new AtomicInteger(0);
        this.pingExecutor = Executors.newSingleThreadScheduledExecutor();
        this.keepaliveExecutor = Executors.newSingleThreadScheduledExecutor();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    }

    public String getListenKeyFromMexc() {
        MexcLoginData loginData = mexcConfig.getLoginData();
        if (loginData == null) {
            throw new RuntimeException("Method getListenKey: MexcLoginData is null.");
        }
        Map<String, String> params = new LinkedHashMap<>();
        String signedUrl = getSignedUrl(params, loginData);
        try {
            ResponseEntity<String> response = restClient
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
                log.error("Failed to create MexcWebSocket stream, status code: {}, body: {}",
                        response.getStatusCode(), response.getBody());
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
            shutdownAndAwaitTermination(pingExecutor);
            shutdownAndAwaitTermination(keepaliveExecutor);
            shutdownAndAwaitTermination(heartbeatExecutor);
            if (closeCode != CloseReason.CloseCodes.NORMAL_CLOSURE) {
                scheduleReconnect();
            }
        } else {
            log.info("Method onClose: MexcWebSocket connection closed, no close reason provided.");
        }
    }

    public void onError() {
        shutdownAndAwaitTermination(pingExecutor);
        shutdownAndAwaitTermination(keepaliveExecutor);
        shutdownAndAwaitTermination(heartbeatExecutor);
        scheduleReconnect();
    }

    private void startKeepaliveExecutor() {
        if (!webSocketClient.getSession().get().isOpen()) {
            log.error("Method startKeepaliveExecutor: User MexcWebSocket session is not open.");
            return;
        }
        keepaliveExecutor.scheduleAtFixedRate(() -> {
            try {
                keepAlive();
            } catch (Exception e) {
                log.error("Method keepAlive: Error during keepalive MexcWebSocket listenKey: {}",
                        e.getMessage());
            }
        }, KEEPALIVE_INTERVAL, KEEPALIVE_INTERVAL, TimeUnit.SECONDS);
    }

    private void keepAlive() {
        MexcLoginData loginData = mexcConfig.getLoginData();
        if (loginData == null) {
            log.error("Method keepAlive: MexcLoginData is null.");
            return;
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("listenKey", listenKey);
        String signedUrl = getSignedUrl(params, loginData);
        ResponseEntity<String> response = restClient
                .put()
                .uri(signedUrl)
                .header(MEXC_API_KEY_HEADER, loginData.getApiKey())
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_APPLICATION_JSON)
                .retrieve()
                .toEntity(String.class);

        log.info("Keepalive response code: {}", response.getStatusCode());
        log.info("Keepalive response body: {}", response.getBody());
    }

    private String getSignedUrl(@NonNull Map<String, String> params, @NonNull MexcLoginData loginData) {
        params.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));
        String rawQueryString = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        String signature = SignatureUtil.createSignature(loginData.getApiSecret(), rawQueryString);
        params.put("signature", signature);
        String encodedQueryString = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return apiUrl + USER_DATA_STREAM_URL + encodedQueryString;
    }

    private void scheduleReconnect() {
        long delay = 5;
        log.info("Scheduling MexcWebSocket reconnection in {} seconds", delay);
        reconnectAttempts.incrementAndGet();
        log.info("Scheduling MexcWebSocket reconnection in {} seconds (attempt {})...",
                delay, reconnectAttempts.get());
        reconnectExecutor.schedule(() -> {
            try {
                log.info("Attempting to reconnect MexcWebSocket...");
                String newListenKey = this.getListenKeyFromMexc();
                if (newListenKey != null) {
                    log.info("Renewed listenKey: {}", newListenKey);
                    String newWsUrl = buildWebSocketUrlWithListenKey(webSocketBaseUrl);
                    log.info("New MexcWebSocket URL: {}", newWsUrl);
                    webSocketClient.setWebSocketUrlWithListenKey(newWsUrl);
                    resetExecutors();
                    pingFailureCounter.set(0);
                    reconnectInProgress.set(false);
                    webSocketClient.getSession().set(null);
                    webSocketClient.connect();
                } else {
                    log.error("Failed to renew listenKey; aborting reconnection attempt.");
                    reconnectInProgress.set(false);
                }
            } catch (Exception e) {
                log.error("User Data WebSocket reconnection failed: {}", e.getMessage());
                reconnectInProgress.set(false);
            }
        }, delay, TimeUnit.SECONDS);
    }

    public String buildWebSocketUrlWithListenKey(@NonNull String baseUrl) {
        return baseUrl + "?listenKey=" + listenKey;
    }

    public void subscribeToChannel(@NonNull String channel) {
        Session session = webSocketClient.getSession().get();
        if (session == null || !session.isOpen()) {
            log.error("Method subscribeToChannel: MexcWebSocket Session is not available or not open.");
            return;
        }
        if (session.isOpen()) {
            String message = String.format(SUBSCRIPTION_TEMPLATE, channel);
            webSocketClient.sendMessage(message);
            log.info("Sent subscription message to MexcWebSocket: {}", message);
        } else {
            log.warn("Cannot subscribe, session is not open.");
        }
    }

    public void unsubscribeFromChannel(@NonNull String channel) {
        Session session = webSocketClient.getSession().get();
        if (session == null || !session.isOpen()) {
            log.error("Method unsubscribeFromChannel: MexcWebSocket Session is not available or not open.");
            return;
        }
        if (session.isOpen()) {
            String message = String.format(UNSUBSCRIPTION_TEMPLATE, channel);
            webSocketClient.sendMessage(message);
            log.info("Sent unsubscribe message to MexcWebSocket: {}", message);
        } else {
            log.warn("Cannot unsubscribe, session is not open.");
        }
    }

    private void startPingExecutor() {
        Session session = webSocketClient.getSession().get();
        if (session == null || !session.isOpen()) {
            log.error("Method startPingExecutor: MexcWebSocket Session is not available or not open.");
            return;
        }
        pingExecutor.scheduleAtFixedRate(() -> {
            try {
                Session currentSession = webSocketClient.getSession().get();
                if (currentSession != null && currentSession.isOpen()) {
                    sendPing();
                } else {
                    scheduleReconnect();
                }
            } catch (Exception e) {
                log.error("Method sendPing: Error sending ping to MexcWebSocket: {}", e.getMessage());
            }
        }, PING_INTERVAL, PING_INTERVAL, TimeUnit.SECONDS);
    }

    private void sendPing() {
        Session currentSession = webSocketClient.getSession().get();
        if (currentSession == null || !currentSession.isOpen()) {
            log.error("Method sendPing: Session is not available or not open.");
            triggerReconnectIfNeeded();
        } else {
            String pingMessage = createPingMessage();
            currentSession.getAsyncRemote().sendText(pingMessage, result -> {
                if (!result.isOK()) {
                    int failures = pingFailureCounter.incrementAndGet();
                    log.error("Ping failed ({} consecutive failures): {}",
                            failures, result.getException().getMessage());
                    if (failures >= MAX_PING_FAILURES) {
                        triggerReconnectIfNeeded();
                    }
                } else {
                    pingFailureCounter.set(0);
                }
            });
        }
    }

    public void onPongReceived() {
        lastPongTime.set(System.currentTimeMillis());
        log.info("Heartbeat updated on PONG: {}", lastPongTime.get());
    }

    private void startHeartbeatMonitor() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - lastPongTime.get();
            if (elapsed > HEARTBEAT_TIMEOUT_MS) {
                log.error("Heartbeat timeout: No PONG received in {} ms", elapsed);
                triggerReconnectIfNeeded();
            }
        }, HEARTBEAT_TIMEOUT_MS, HEARTBEAT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void triggerReconnectIfNeeded() {
        if (reconnectInProgress.compareAndSet(false, true)) {
            scheduleReconnect();
        }
    }

    private String createPingMessage() {
        return String.format(PING_MESSAGE);
    }

    private void resetExecutors() {
        shutdownAndAwaitTermination(pingExecutor);
        shutdownAndAwaitTermination(keepaliveExecutor);
        shutdownAndAwaitTermination(heartbeatExecutor);
        pingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("PingExecutor");
            return t;
        });
        keepaliveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("KeepaliveExecutor");
            return t;
        });
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("HeartBeatExecutor");
            return t;
        });
    }

    private void shutdownAndAwaitTermination(ExecutorService executor) {
        if (executor != null && !executor.isShutdown()) {
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

    @PreDestroy
    public void onShutdown() {
        shutdownAndAwaitTermination(pingExecutor);
        shutdownAndAwaitTermination(keepaliveExecutor);
        shutdownAndAwaitTermination(heartbeatExecutor);
        shutdownAndAwaitTermination(reconnectExecutor);
    }
}
