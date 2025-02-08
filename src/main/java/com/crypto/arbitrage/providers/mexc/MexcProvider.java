package com.crypto.arbitrage.providers.mexc;

import jakarta.websocket.ClientEndpoint;
import org.springframework.stereotype.Component;
import velox.api.layer0.annotations.Layer0LiveModule;
import velox.api.layer0.live.ExternalLiveBaseProvider;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.data.LoginData;
import velox.api.layer1.data.OrderSendParameters;
import velox.api.layer1.data.OrderUpdateParameters;
import velox.api.layer1.data.SubscribeInfo;

import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


@Component
public class MexcProvider extends ExternalLiveBaseProvider {
    AtomicBoolean isLogin = new AtomicBoolean();
    ExecutorService keyListenerExecutorService = Executors.newSingleThreadExecutor();

    @Override
    public void login(LoginData loginData) {
        // cast to MexcLoginData
        MexcLoginData mexcLoginData = (MexcLoginData) loginData;

        // create put request to server to get keyListener
        // send put request
//        String keyListener = "someValue";

        // create ws connectio n

    }

    private void createTimerToUpdateKeyListener() {

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


    void onMessage(String message) {
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
}
