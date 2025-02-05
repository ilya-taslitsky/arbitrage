package com.crypto.arbitrage.providers.mexc;

import org.springframework.stereotype.Component;
import velox.api.layer0.annotations.Layer0LiveModule;
import velox.api.layer0.live.ExternalLiveBaseProvider;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.data.LoginData;
import velox.api.layer1.data.OrderSendParameters;
import velox.api.layer1.data.OrderUpdateParameters;
import velox.api.layer1.data.SubscribeInfo;

@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
@Layer0LiveModule(shortName = "MEXC", fullName = "MEXC-Provider")
@Component
public class MexcProvider extends ExternalLiveBaseProvider {
    @Override
    public void login(LoginData loginData) {

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
}
