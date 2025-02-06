package com.crypto.arbitrage.providers.mexc.websocket;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import jakarta.websocket.Session;
import jakarta.websocket.CloseReason;
import jakarta.annotation.PreDestroy;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import com.crypto.arbitrage.providers.mexc.model.MexcLoginData;
import com.crypto.arbitrage.providers.mexc.common.SignatureUtil;

import java.util.Map;
import java.time.Instant;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Service
public class MexcWebSocketStateService {

    private static final long PING_INTERVAL = 30;
    private static final long BASE_RECONNECT_DELAY = 5;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long KEEPALIVE_INTERVAL = 30 * 60; // 30 minutes in seconds
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String MEXC_API_KEY_HEADER = "X-MEXC-APIKEY";
    private static final String PING_MESSAGE = "{\"method\":\"PING\"}";
    private static final String USER_DATA_STREAM_URL = "/api/v3/userDataStream?";
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private static final String SUBSCRIPTION_TEMPLATE = "{\"method\":\"SUBSCRIPTION\",\"params\":[\"%s\"]}";

    private final String apiUrl;
    @Setter
    private MexcLoginData loginData;
    private final RestClient restClient;
    private final String webSocketBaseUrl;
    private final ObjectMapper objectMapper;
    private ScheduledExecutorService pingExecutor;
    private final AtomicInteger reconnectAttempts;
    private final AtomicReference<Session> sessionRef;
    private final MexcWebSocketClient webSocketClient;
    private final ScheduledExecutorService keepaliveExecutor;
    private final ScheduledExecutorService reconnectExecutor;

    @Getter
    private String listenKey;

    public MexcWebSocketStateService(@Value("${mexc.api.url}") String apiUrl,
                                     @Value("${mexc.api.websocketBaseUrl}") String webSocketBaseUrl,
                                     MexcWebSocketClient webSocketClient,
                                     RestClient restClient,
                                     ObjectMapper objectMapper) {
        this.apiUrl = apiUrl;
        this.objectMapper = objectMapper;
        this.webSocketClient = webSocketClient;
        this.webSocketBaseUrl = webSocketBaseUrl;
        this.restClient = restClient;
        this.sessionRef = new AtomicReference<>(null);
        this.reconnectAttempts = new AtomicInteger(0);
        this.keepaliveExecutor = Executors.newSingleThreadScheduledExecutor();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public String getListenKey(@NonNull MexcLoginData loginData) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));
        // Build the raw query string (for signing)
        String rawQueryString = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        // Generate the signature using your API secret
        String signature = SignatureUtil.createSignature(loginData.getApiSecret(), rawQueryString);
        params.put("signature", signature);
        // Build a URL-encoded query string
        String encodedQueryString = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String url = apiUrl + USER_DATA_STREAM_URL + encodedQueryString;

        try {
            ResponseEntity<String> response = restClient
                    .post()
                    .uri(url)
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

    public void onOpen(@NonNull Session session) {
        sessionRef.set(session);
        reconnectAttempts.set(0);
        log.info("User MexcWebSocket connection opened: {}", session.getId());
        startKeepaliveExecutor();
        startPingExecutor(session);
    }

    public void onClose(CloseReason closeReason) {
        if (closeReason != null) {
            CloseReason.CloseCode closeCode = closeReason.getCloseCode();
            log.info("User MexcWebSocket connection closed: {} {}",
                    closeCode, closeReason.getReasonPhrase());
            stopKeepaliveExecutor();
            if (closeCode != CloseReason.CloseCodes.NORMAL_CLOSURE) {
                scheduleReconnect();
            }
        } else {
            log.info("MexcWebSocket connection closed, no close reason provided.");
        }
    }

    public void onError(Throwable throwable) {
        String message = "";
        if (throwable != null) {
            message = throwable.getMessage();
        }
        log.error("MexcWebSocket error: {}", message);
        stopKeepaliveExecutor();
        scheduleReconnect();
    }

    private void startKeepaliveExecutor() {
        keepaliveExecutor.scheduleAtFixedRate(() -> {
            try {
                keepAlive();
            } catch (Exception e) {
                log.error("Error during keepalive MexcWebSocket listenKey: {}", e.getMessage());
            }
        }, KEEPALIVE_INTERVAL, KEEPALIVE_INTERVAL, TimeUnit.SECONDS);
    }

    private void stopKeepaliveExecutor() {
        if (!keepaliveExecutor.isShutdown()) {
            keepaliveExecutor.shutdownNow();
        }
    }

    // Sends a PUT request to extend the listenKey validity.
    private void keepAlive() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("listenKey", listenKey);
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
        String url = apiUrl + USER_DATA_STREAM_URL + encodedQueryString;

        ResponseEntity<String> response = restClient
                .put()
                .uri(url)
                .header(MEXC_API_KEY_HEADER, loginData.getApiKey())
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_APPLICATION_JSON)
                .retrieve()
                .toEntity(String.class);

        log.info("Keepalive response code: {}", response.getStatusCode());
        log.info("Keepalive response body: {}", response.getBody());
    }

    private void scheduleReconnect() {
        if (reconnectAttempts.get() < MAX_RECONNECT_ATTEMPTS) {
            long delay = BASE_RECONNECT_DELAY * (long) Math.pow(2, reconnectAttempts.get());
            reconnectAttempts.incrementAndGet();
            log.info("Scheduling MexcWebSocket reconnection in {} seconds (attempt {})...",
                    delay, reconnectAttempts.get());
            reconnectExecutor.schedule(() -> {
                try {
                    log.info("Attempting to reconnect MexcWebSocket...");
                    // Request a new listenKey
                    String newListenKey = this.getListenKey();
                    if (newListenKey != null) {
                        log.info("Renewed listenKey: {}", newListenKey);
                        // Build new WebSocket URL with the renewed listenKey.
                        String newWsUrl = buildWebSocketUrlWithListenKey(webSocketBaseUrl);
                        log.info("New MexcWebSocket URL: {}", newWsUrl);
                        // Update the client's URL.
                        webSocketClient.setWebSocketUrlWithListenKey(newWsUrl);
                        // Now call connect() on the client to establish a new connection.
                        webSocketClient.connect();
                    } else {
                        log.error("Failed to renew listenKey; aborting reconnection attempt.");
                    }
                } catch (Exception e) {
                    log.error("User Data WebSocket reconnection failed: {}", e.getMessage());
                    // Retry reconnection if possible.
                    scheduleReconnect();
                }
            }, delay, TimeUnit.SECONDS);
        } else {
            log.error("Max reconnection attempts reached for MexcWebSocket. Giving up.");
        }
    }

    // Builds the full WebSocket URL using the current listenKey.
    public String buildWebSocketUrlWithListenKey(@NonNull String baseUrl) {
        return baseUrl + "?listenKey=" + listenKey;
    }

    // Sends a subscription message for a given topic over the open WebSocket connection.
    public void subscribeToChannel(@NonNull String channel) {
        Session session = sessionRef.get();
        if (session != null && session.isOpen()) {
            String message = String.format(SUBSCRIPTION_TEMPLATE, channel);
            try {
                session.getBasicRemote().sendText(message);
                log.info("Sent subscription message to MexcWebSocket: {}", message);
            } catch (IOException e) {
                log.error("Error sending subscription message to MexcWebSocket: {}", e.getMessage());
            }
        } else {
            log.warn("Cannot subscribe, session is not open.");
        }
    }

    private void startPingExecutor(Session session) {
        if (pingExecutor == null || pingExecutor.isTerminated()) {
            pingExecutor = Executors.newSingleThreadScheduledExecutor();
            pingExecutor.scheduleAtFixedRate(() -> {
                try {
                    if (session != null && session.isOpen()) {
                        sendPing(session);
                    }
                } catch (Exception e) {
                    log.error("Error sending ping to MexcWebSocket: {}", e.getMessage());
                }
            }, PING_INTERVAL, PING_INTERVAL, TimeUnit.SECONDS);
        }
    }

    private void sendPing(Session session) {
        String pingMessage = createPingMessage();
        sendMessage(session, pingMessage);
    }

    private void sendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
            if (!message.contains("PING")) {
                log.info("Sent message: {}", message);
            }
        } catch (IOException e) {
            log.error("Error sending message to MexcWebSocket due to IOException: {}", e.getMessage());
        }
    }

    private String createPingMessage() {
        return String.format(PING_MESSAGE);
    }

    @PreDestroy
    public void onShutdown() {
        if (pingExecutor != null && !pingExecutor.isTerminated()) {
            pingExecutor.shutdownNow();
        }
        stopKeepaliveExecutor();
        reconnectExecutor.shutdownNow();
    }
}
