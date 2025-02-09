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
    private static final  String BOOK_DEPTH = "5";
    private static final String DEALS_TOPIC = "spot@public.deals.v3.api@%s";
    private static final String USER_ACCOUNT_UPDATE_TOPIC = "spot@private.account.v3.api";
    private static final String PARTIAL_DEPTH_TOPIC = "spot@public.limit.depth.v3.api@%s@%s";

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

    public void subscribeToTopic(@NonNull String symbol) {
        if(!mexcWebSocketClient.isSessionOpen()) {
            log.error("SubscribeToTopic: WebSocket is not open");
            return;
        }
        String dealsTopic = String.format(DEALS_TOPIC, symbol);
        String partialDepthTopic = String.format(PARTIAL_DEPTH_TOPIC, symbol, BOOK_DEPTH);
        webSocketStateService.subscribeToChannel(dealsTopic);
        webSocketStateService.subscribeToChannel(partialDepthTopic);
    }

    public void unsubscribeFromTopic(@NonNull String symbol) {
        if(!mexcWebSocketClient.isSessionOpen()) {
            log.error("UnsubscribeFromTopic: WebSocket is not open");
            return;
        }
        String dealsTopic = String.format(DEALS_TOPIC, symbol);
        String partialDepthTopic = String.format(PARTIAL_DEPTH_TOPIC, symbol, BOOK_DEPTH);
        webSocketStateService.unsubscribeFromChannel(dealsTopic);
        webSocketStateService.unsubscribeFromChannel(partialDepthTopic);
    }

    public void closeWebSocket() {
        if (mexcWebSocketClient.isSessionOpen()) {
            mexcWebSocketClient.disconnect();
        }else {
            log.info("WebSocket is already closed.");
        }
    }
}
