package com.crypto.arbitrage.providers.mexc.websocket;

import lombok.Setter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import com.crypto.arbitrage.providers.mexc.model.MexcLoginData;

@Slf4j
@Service
public class MexcWebSocketManager {
    @Setter
    private MexcLoginData loginData;
    private final String baseWebsocketUrl;
    private static final String USER_ACCOUNT_UPDATE_TOPIC = "spot@private.account.v3.api";

    private final MexcWebSocketClient mexcWebSocketClient;
    private final MexcWebSocketStateService webSocketStateService;

    @Autowired
    public MexcWebSocketManager(@Value("${mexc.api.websocketBaseUrl}") String baseWebsocketUrl,
                                MexcWebSocketClient mexcWebSocketClient,
                                MexcWebSocketStateService webSocketStateService) {
        this.mexcWebSocketClient = mexcWebSocketClient;
        this.webSocketStateService = webSocketStateService;
        this.baseWebsocketUrl = baseWebsocketUrl;
    }

    public void openWebSocket() {
        if (loginData == null) {
            throw new RuntimeException("Login data is null");
        }

        if (mexcWebSocketClient.isSessionOpen()) {
            log.info("WebSocket is already open.");
            return;
        }
        webSocketStateService.setLoginData(loginData);
        String newListenKey = webSocketStateService.getListenKey(loginData);
        if (newListenKey != null) {
            String fullUrl = webSocketStateService.buildWebSocketUrlWithListenKey(baseWebsocketUrl);
            log.info("Opening User Data WebSocket with URL: {}", fullUrl);
            mexcWebSocketClient.setWebSocketUrlWithListenKey(fullUrl);
            try {
                mexcWebSocketClient.connect();
            } catch (Exception e) {
                log.error("Failed to open MexcWebSocket", e);
                throw new RuntimeException(e);
            }
        } else {
            log.error("Could not obtain a new listenKey, aborting connection.");
        }
        // Subscribe to topic to maintain connection.
        webSocketStateService.subscribeToChannel(USER_ACCOUNT_UPDATE_TOPIC);
    }

    public void subscribeToTopic(@NonNull String topic) {
        if(!mexcWebSocketClient.isSessionOpen()) {
            throw new RuntimeException("WebSocket is not open");
        }
        webSocketStateService.subscribeToChannel(topic);
    }

    public void closeWebSocket() {
        if (mexcWebSocketClient.isSessionOpen()) {
            mexcWebSocketClient.disconnect();
        }
    }
}
