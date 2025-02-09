package com.crypto.arbitrage.providers.mexc.websocket;

import com.crypto.arbitrage.providers.mexc.model.event.WebSocketSessionStatusEvent;
import com.crypto.arbitrage.providers.mexc.service.parser.MexcMessageDispatcher;
import jakarta.annotation.PreDestroy;
import jakarta.websocket.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@ClientEndpoint
public class MexcWebSocketClient {

    private final static String PONG_MESSAGE = "\"msg\":\"PONG\"";

    private final MexcMessageDispatcher dispatcher;
    @Setter
    private String webSocketUrlWithListenKey;
    private final ApplicationEventPublisher publisher;
    private final MexcWebSocketStateService mexcWebSocketStateService;
    @Getter
    private AtomicReference<Session> session = new AtomicReference<>();

    @Autowired
    public MexcWebSocketClient(MexcMessageDispatcher dispatcher, @Value("${mexc.api.websocketBaseUrl}") String baseUrl,
                               @Lazy MexcWebSocketStateService mexcWebSocketStateService,
                               ApplicationEventPublisher publisher) {
        this.dispatcher = dispatcher;
        this.webSocketUrlWithListenKey = baseUrl;
        this.mexcWebSocketStateService = mexcWebSocketStateService;
        this.publisher = publisher;
    }


    public void connect() {
        log.info("Method connect: Connecting to MexcWebSocket at: {}", webSocketUrlWithListenKey);
        if (session.get() != null && session.get().isOpen()) {
            log.warn("Method connect: User MexcWebSocket session is already open.");
            return;
        }
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            Session activeSession = container.connectToServer(this,
                    new URI(webSocketUrlWithListenKey));
            this.session.set(activeSession);
        } catch (DeploymentException | IOException | URISyntaxException e) {
            publisher.publishEvent(new WebSocketSessionStatusEvent(false));
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
        Session currentSession = session.get();
        if (currentSession == null || !currentSession.isOpen()) {
            log.error("Method disconnect: MexcWebSocket Session is not available or not open.");
            return;
        }
        if (session.get().isOpen()) {
            closeSession(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE,
                    "Method disconnect: Application shutdown"));
            log.info("Method disconnect: MexcWebSocket session closed.");
        } else {
            log.info("Method disconnect: MexcWebSocket session is already closed.");
        }
    }

    @OnOpen
    public void onOpen() {
        publisher.publishEvent(new WebSocketSessionStatusEvent(true));
        log.info("Method onOpen: MexcWebSocket session opened.");
        Executors.newSingleThreadScheduledExecutor()
                .schedule(mexcWebSocketStateService::onOpen, 1, TimeUnit.SECONDS);
    }

    @OnMessage
    public void onMessage(String message) {
        if (message.contains(PONG_MESSAGE)) {
            mexcWebSocketStateService.onPongReceived();
            log.info("Received PONG message: {}", message);
            return;
        }
        dispatcher.dispatchMessage(message);
    }

    @OnClose
    public void onClose(CloseReason closeReason) {
        Session currentSession = session.get();
        if (currentSession != null) {
            try {
                currentSession.close();
                log.info("Method onClose: MexcWebSocket session closed reason {}, {}",
                        closeReason.getCloseCode(), closeReason.getReasonPhrase());
            } catch (IOException e) {
                session.set(null);
                log.error("Method onClose: " + "Error closing MexcWebSocket session after invoke close()" +
                                " closeReason {} : exception {}",
                        closeReason.getCloseCode(),
                        e.getMessage());
            } finally {
                session.set(null);
            }
            publisher.publishEvent(new WebSocketSessionStatusEvent(false));
            mexcWebSocketStateService.onClose(closeReason);
        } else {
            log.info("Method onClose: session is already closed.");
        }
    }

    @OnError
    public void onError(Throwable thr) {
        Session currentSession = session.get();
        if (currentSession != null) {
            try {
                currentSession.close();
                log.info("Method onError: MexcWebSocket session closed.");
                mexcWebSocketStateService.onError();
            } catch (IOException e) {
                log.error("Method onError: Error closing session after error {}: {}",
                        thr.getMessage(), e.getMessage());
            } finally {
                session.set(null);
            }
        } else {
            log.warn("Method onError: Session is already null.");
        }
        log.error("Method onError: MexcWebSocket error: {}", thr.getMessage());
        publisher.publishEvent(new WebSocketSessionStatusEvent(false));
    }

    public void sendMessage(@NonNull String message) {
        Session activeSession = session.get();
        if (activeSession == null || !activeSession.isOpen()) {
            log.error("Method sendMessage: MexcWebSocket session is not open.");
        } else {
            activeSession.getAsyncRemote().sendText(message, result -> {
                if (!result.isOK()) {
                    log.error("Failed to send message: {}", result.getException().getMessage());
                }
            });
        }
    }

    private void closeSession(CloseReason closeReason) {
        try {
            session.get().close(closeReason);
            publisher.publishEvent(new WebSocketSessionStatusEvent(false));
        } catch (IOException e) {
            log.error("Method closeSession: Error on closing MexcWebSocket session: reason {} : exception {}",
                    closeReason.getCloseCode(),
                    e.getMessage());
        }
    }

    @PreDestroy
    public void onShutdown() {
        disconnect();
    }
}