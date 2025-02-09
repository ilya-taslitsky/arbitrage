package com.crypto.arbitrage.providers.mexc;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import velox.api.layer1.data.LoginData;
import velox.api.layer1.data.SubscribeInfo;
import org.springframework.stereotype.Component;
import velox.api.layer1.data.OrderSendParameters;
import velox.api.layer1.data.OrderUpdateParameters;
import velox.api.layer0.annotations.Layer0LiveModule;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer0.live.ExternalLiveBaseProvider;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import com.crypto.arbitrage.providers.mexc.model.MexcLoginData;
import com.crypto.arbitrage.providers.mexc.websocket.MexcWebSocketManager;

@Slf4j
@Component
@RequiredArgsConstructor
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
@Layer0LiveModule(shortName = "MEXC", fullName = "MEXC-Provider")
public class MexcProvider extends ExternalLiveBaseProvider {

    private final MexcWebSocketManager mexcWebSocketManager;

    @Override
    public void login(@NonNull LoginData loginData) {
        if (isLoginDataValid(loginData)) {
            mexcWebSocketManager.setLoginData((MexcLoginData) loginData);
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
        log.info("formatPrice: {}, {}", s, v);
        return null;
    }

    @Override
    public void subscribe(@NonNull SubscribeInfo subscribeInfo) {
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
        // TODO
        log.info("OrderSendParameters: {}", orderSendParameters);
    }

    @Override
    public void updateOrder(@NonNull OrderUpdateParameters orderUpdateParameters) {
        // TODO
        log.info("OrderUpdateParameters: {}", orderUpdateParameters);
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
