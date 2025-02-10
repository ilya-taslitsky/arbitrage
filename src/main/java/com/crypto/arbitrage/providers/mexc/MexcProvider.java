package com.crypto.arbitrage.providers.mexc;


import com.crypto.arbitrage.providers.mexc.config.MexcConfig;
import com.crypto.arbitrage.providers.mexc.model.order.MexcLoginData;
import com.crypto.arbitrage.providers.mexc.model.order.NewOrderReq;
import com.crypto.arbitrage.providers.mexc.service.MexcOrderService;
import com.crypto.arbitrage.providers.mexc.websocket.MexcWebSocketManager;
import com.crypto.arbitrage.providers.mexc.websocket.WebSocketSessionStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import velox.api.layer0.annotations.Layer0LiveModule;
import velox.api.layer0.live.ExternalLiveBaseProvider;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.data.LoginData;
import velox.api.layer1.data.OrderSendParameters;
import velox.api.layer1.data.OrderUpdateParameters;
import velox.api.layer1.data.SubscribeInfo;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
@Layer0LiveModule(shortName = "MEXC", fullName = "MEXC-Provider")
public class MexcProvider extends ExternalLiveBaseProvider {

    private final static String MEXC_WEB_SOCKET_SESSION_ALREADY_ACTIVE =
            "MexcWebSocket session is already active.";
    private final static String MEXC_WEB_SOCKET_SESSION_NOT_ACTIVE =
            "MexcWebSocket session is not active.";

    private final AtomicBoolean isSessionActive = new AtomicBoolean(false);

    private final MexcConfig mexcConfig;
    private final MexcOrderService mexcOrderService;
    private final MexcWebSocketManager mexcWebSocketManager;

    @Override
    public void login(@NonNull LoginData loginData) {
        if (isSessionActive.get()) {
            log.error(MEXC_WEB_SOCKET_SESSION_ALREADY_ACTIVE);
            return;
        }
        if (isLoginDataValid(loginData)) {
            mexcConfig.setLoginData((MexcLoginData) loginData);
            mexcWebSocketManager.openWebSocket();
        } else {
            log.error("LoginData must be of type MexcLoginData " +
                    "and have apiKey and apiSecret set.");
        }
    }

    @Override
    public String getSource() {
        return "Mexc provider";
    }

    @Override
    public void close() {
        mexcWebSocketManager.closeWebSocket();
    }

    @Override
    public String formatPrice(@NonNull String s, double v) {
        // TODO
        // What i have to do here?
        log.info("formatPrice: {}, {}", s, v);
        return null;
    }

    @Override
    public void subscribe(@NonNull SubscribeInfo subscribeInfo) {
        if (!isSessionActive.get()) {
            log.error(MEXC_WEB_SOCKET_SESSION_NOT_ACTIVE);
            return;
        }
        if (subscribeInfo.symbol == null) {
            log.error("SubscribeInfo symbol is null.");
            return;
        }
        mexcWebSocketManager.subscribeToTopic(subscribeInfo.symbol);
    }

    @Override
    public void unsubscribe(@NonNull String symbol) {
        mexcWebSocketManager.unsubscribeFromTopic(symbol);
    }

    @Override
    public void sendOrder(@NonNull OrderSendParameters orderSendParameters) {
        if (orderSendParameters instanceof NewOrderReq newOrderReq) {
            mexcOrderService.sendOrder(newOrderReq);
            log.info("OrderSendParameters: {}", orderSendParameters);
        } else {
            log.error("OrderSendParameters must be of type NewOrderReq.");
        }
    }

    @Override
    public void updateOrder(@NonNull OrderUpdateParameters orderUpdateParameters) {
        log.info("Not implemented.");
    }

    @EventListener
    public void onWebSocketSessionStatusEvent(WebSocketSessionStatusEvent event) {
        isSessionActive.set(event.isActive());
        log.info("WebSocket session is now {}", event.isActive() ? "active" : "inactive");
    }

    private boolean isLoginDataValid(@NonNull LoginData loginData) {
        return loginData instanceof MexcLoginData mexcLoginData &&
                mexcLoginData.getApiKey() != null &&
                mexcLoginData.getApiSecret() != null;
    }

//    void onMessage(String message) {
//        if (message.contains("tradeData")) {
//            // MexcTradeData tradeData = (MexcTradedata) message;
//            MexTradeData tradeData;
//            boolean isBid = tradeData.getSide().equals("buy");
//            dataListeners.forEach(dataListener -> dataListener.onTrade(tradeData.getSymbolName(), );
//        } else if (message.contains("depthData")) {
//            dataListeners.forEach(dataListener -> dataListener.onDepth());
//        } else if (message.contains("authSuccessful")) {
//            isLogin.set(true);
//        }
}
