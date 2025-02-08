package com.crypto.arbitrage.providers.mexc;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
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
    public void login(LoginData loginData) {
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
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    public String formatPrice(String s, double v) {
        return null;
    }

    @Override
    public void subscribe(SubscribeInfo subscribeInfo) {
        // get ws client
        // ws.subsribe(symbol);
    }

    @Override
    public void unsubscribe(String s) {

    }

    @Override
    public void sendOrder(OrderSendParameters orderSendParameters) {

    }

    @Override
    public void updateOrder(OrderUpdateParameters orderUpdateParameters) {

    }


    private boolean isLoginDataValid(LoginData loginData) {
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
