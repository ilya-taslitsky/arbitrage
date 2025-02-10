package com.crypto.arbitrage.providers.mexc.websocket;

import lombok.Setter;
import jakarta.websocket.*;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.io.IOException;
import java.net.URISyntaxException;

@Slf4j
@Service
@ClientEndpoint
public class MexcWebSocketClient {

    @Setter
    private String webSocketUrlWithListenKey; // Full URL including listenKey, e.g. "wss://wbs.mexc.com/ws?listenKey=..."

    private Session session;
    private final MexcWebSocketStateService mexcWebSocketStateService;
    private final ApplicationEventPublisher publisher;

    @Autowired
    public MexcWebSocketClient(@Value("${mexc.api.websocketBaseUrl}") String baseUrl,
                               @Lazy MexcWebSocketStateService mexcWebSocketStateService,
                               ApplicationEventPublisher publisher) {
        this.webSocketUrlWithListenKey = baseUrl;
        this.mexcWebSocketStateService = mexcWebSocketStateService;
        this.publisher = publisher;
    }


    public void connect() {
        log.info("Connecting to MexcWebSocket at: {}", webSocketUrlWithListenKey);
        if (isSessionOpen()) {
            log.warn("User MexcWebSocket session is already open.");
            return;
        }
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            session = container.connectToServer(this, new URI(webSocketUrlWithListenKey));
            publisher.publishEvent(new WebSocketSessionStatusEvent(true));
        } catch (DeploymentException | IOException | URISyntaxException e) {
            publisher.publishEvent(new WebSocketSessionStatusEvent(false));
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
        if (isSessionOpen()) {
            closeSession(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE,
                    "Application shutdown"));
            publisher.publishEvent(new WebSocketSessionStatusEvent(false));
        }
    }

    public boolean isSessionOpen() {
        return session != null && session.isOpen();
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        publisher.publishEvent(new WebSocketSessionStatusEvent(true));
        mexcWebSocketStateService.onOpen(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // TODO: Handle incoming messages
        log.info("Received message: {}", message);
    }

    @OnClose
    public void onClose(CloseReason closeReason) {
        try {
            session.close();
        } catch (IOException e) {
            session = null;
            log.error("Error closing MexcWebSocket session after invoke close() closeReason {} : exception {}",
                    closeReason.getCloseCode(),
                    e.getMessage());
        }
        publisher.publishEvent(new WebSocketSessionStatusEvent(false));
        mexcWebSocketStateService.onClose(closeReason);
    }

    @OnError
    public void onError(Throwable thr) {
        try {
            session.close();
        } catch (IOException e) {
            session = null;
            log.error("Error closing MexcWebSocket session after error {} : exception {}",
                    thr.getMessage(),
                    e.getMessage());
        }
        log.error("MexcWebSocket error: {}", thr.getMessage());
        publisher.publishEvent(new WebSocketSessionStatusEvent(false));
        mexcWebSocketStateService.onError(thr);
    }

    public void sendMessage(String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error("Error sending message to MexcWebSocket: {}", e.getMessage());
        }
    }

    private void closeSession(CloseReason closeReason) {
        try {
            session.close(closeReason);
            publisher.publishEvent(new WebSocketSessionStatusEvent(false));
        } catch (IOException e) {
            log.error("Error on closing MexcWebSocket session: reason {} : exception {}",
                    closeReason.getCloseCode(),
                    e.getMessage());
        }
    }

    @PreDestroy
    public void onShutdown() {
        disconnect();
    }
}