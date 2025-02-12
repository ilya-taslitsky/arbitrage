package com.crypto.arbitrage.providers.mexc.websocket;

import com.crypto.arbitrage.providers.mexc.model.order.MexcLoginData;
import jakarta.websocket.Session;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MexcWebSocketManager {

    @Setter
    private MexcLoginData loginData;

    private final String baseWebsocketUrl;
    private static final String BOOK_DEPTH = "5";
    private static final String DEALS_TOPIC =
            "spot@public.deals.v3.api@%s";
    private static final String USER_ACCOUNT_UPDATE_TOPIC =
            "spot@private.account.v3.api";
    private static final String USER_ACCOUNT_DEAL_TOPIC =
            "spot@private.deals.v3.api";
    private static final String USER_ACCOUNT_ORDERS_TOPIC =
            "spot@private.orders.v3.api";
    private static final String PARTIAL_DEPTH_TOPIC =
            "spot@public.limit.depth.v3.api@%s@%s";

    private final MexcWebSocketClient mexcWebSocketClient;
    private final MexcWebSocketStateService webSocketStateService;

    @Autowired
    public MexcWebSocketManager(@Value("${mexc.api.websocketBaseUrl}") String baseWebsocketUrl,
                                MexcWebSocketClient mexcWebSocketClient,
                                MexcWebSocketStateService webSocketStateService) {
        this.baseWebsocketUrl = baseWebsocketUrl;
        this.mexcWebSocketClient = mexcWebSocketClient;
        this.webSocketStateService = webSocketStateService;
    }

    public void openWebSocket() {
        Session session = mexcWebSocketClient.getSession().get();
        if (session != null && session.isOpen()) {
            log.info("Method openWebSocket: MexcWebSocket is already open.");
            return;
        }

        String newListenKey = webSocketStateService.getListenKeyFromMexc(loginData);
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

        webSocketStateService.subscribeToChannel(USER_ACCOUNT_DEAL_TOPIC);
        webSocketStateService.subscribeToChannel(USER_ACCOUNT_UPDATE_TOPIC);
        webSocketStateService.subscribeToChannel(USER_ACCOUNT_ORDERS_TOPIC);
    }

    public void subscribeToTopic(@NonNull String symbol) {
        Session session = mexcWebSocketClient.getSession().get();
        if (session == null || !session.isOpen()) {
            log.error("Method subscribeToTopic: WebSocket session is not open");
            return;
        }
        String dealsTopic = String.format(DEALS_TOPIC, symbol);
        String partialDepthTopic = String.format(PARTIAL_DEPTH_TOPIC, symbol, BOOK_DEPTH);
        webSocketStateService.subscribeToChannel(dealsTopic);
        webSocketStateService.subscribeToChannel(partialDepthTopic);
    }

    public void unsubscribeFromTopic(@NonNull String symbol) {
        Session session = mexcWebSocketClient.getSession().get();
        if (session == null || !session.isOpen()) {
            log.error("Method unsubscribeFromTopic: WebSocket session is not open");
            return;
        }
        String dealsTopic = String.format(DEALS_TOPIC, symbol);
        String partialDepthTopic = String.format(PARTIAL_DEPTH_TOPIC, symbol, BOOK_DEPTH);
        webSocketStateService.unsubscribeFromChannel(dealsTopic);
        webSocketStateService.unsubscribeFromChannel(partialDepthTopic);
    }

    public void closeWebSocket() {
        Session session = mexcWebSocketClient.getSession().get();
        if (session != null && session.isOpen()) {
            mexcWebSocketClient.disconnect();
        } else {
            log.info("WebSocket is already closed.");
        }
    }
}
