package com.crypto.arbitrage.providers.mexc;


import com.crypto.arbitrage.providers.mexc.model.account.MexcBalanceEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcDepthEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcExchangeEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcTradeEvent;
import com.crypto.arbitrage.providers.mexc.model.event.MexcWebSocketSessionStatusEvent;
import com.crypto.arbitrage.providers.mexc.model.instrument.MexcSubscribedInstrumentEvent;
import com.crypto.arbitrage.providers.mexc.model.instrument.MexcUnsubscribedInstrumentEvent;
import com.crypto.arbitrage.providers.mexc.model.order.MexcExecutionEvent;
import com.crypto.arbitrage.providers.mexc.model.order.MexcLoginData;
import com.crypto.arbitrage.providers.mexc.model.order.MexcNewOrderReq;
import com.crypto.arbitrage.providers.mexc.model.order.MexcOrderInfoEvent;
import com.crypto.arbitrage.providers.mexc.service.MexcOrderService;
import com.crypto.arbitrage.providers.mexc.websocket.MexcWebSocketManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import velox.api.layer0.annotations.Layer0LiveModule;
import velox.api.layer0.live.ExternalLiveBaseProvider;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.data.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class MexcProvider extends ExternalLiveBaseProvider {
    public static final String NAME = "MEXC";
    @Getter
    private final AtomicBoolean isLoggedIn = new AtomicBoolean(false);
    private final MexcOrderService mexcOrderService;
    private final MexcWebSocketManager mexcWebSocketManager;
    private final Map<String, InstrumentInfo> subscribedInstrumentInfo = new HashMap<>();
    private final ApplicationEventPublisher publisher;


    @Override
    public void login(@NonNull LoginData loginData) {
        if (isLoggedIn.get()) {
            log.warn("Method login: MexcWebSocket session is already active.");
            return;
        }
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
        return NAME;
    }

    @Override
    public void close() {
        mexcWebSocketManager.closeWebSocket();
    }

    @Override
    public String formatPrice(@NonNull String s, double v) {
        log.info("formatPrice: {}, {}", s, v);
        return null;
    }

    @Override
    public void subscribe(@NonNull SubscribeInfo subscribeInfo) {
        if (!isLoggedIn.get()) {
            log.warn("Method subscribe: MexcWebSocket session is not active.");
            return;
        }
        if (subscribeInfo.symbol == null) {
            log.error("SubscribeInfo symbol is null.");
            return;
        }
        if (subscribedInstrumentInfo.containsKey(subscribeInfo.symbol)) {
            log.warn("Already subscribed to symbol {}", subscribeInfo.symbol);
            return;
        }

        SubscribeInfoCrypto subscribeInfoCrypto = (SubscribeInfoCrypto) subscribeInfo;
        double sizeMultiplier = subscribeInfoCrypto.sizeMultiplier;
        double pips = subscribeInfoCrypto.pips;
        InstrumentInfo instrumentInfo = new InstrumentInfo(subscribeInfoCrypto.symbol, NAME, null, pips, 1., subscribeInfo.symbol, true, sizeMultiplier, true);

        // TODO: possibility of data inconsistency if we put instrument info to the map before getting message from server that we subscribed
        subscribedInstrumentInfo.put(subscribeInfo.symbol, instrumentInfo);
        mexcWebSocketManager.subscribeToTopic(subscribeInfo.symbol);
        publisher.publishEvent(new MexcSubscribedInstrumentEvent(instrumentInfo));
        instrumentListeners.forEach(listener -> listener.onInstrumentAdded(instrumentInfo.symbol, instrumentInfo));
    }

    @Override
    public void unsubscribe(@NonNull String symbol) {
        if (!isLoggedIn.get()) {
            log.warn("Method unsubscribe: MexcWebSocket session is not active.");
            return;
        }
        if (!subscribedInstrumentInfo.containsKey(symbol)) {
            log.warn("Not subscribed to symbol {}", symbol);
            return;
        }
        subscribedInstrumentInfo.remove(symbol);
        mexcWebSocketManager.unsubscribeFromTopic(symbol);
        publisher.publishEvent(new MexcUnsubscribedInstrumentEvent(symbol));
        instrumentListeners.forEach(listener -> listener.onInstrumentRemoved(symbol));
    }

    @Override
    public void sendOrder(@NonNull OrderSendParameters orderSendParameters) {
        if (orderSendParameters instanceof MexcNewOrderReq mexcNewOrderReq) {
            mexcOrderService.sendOrder(mexcNewOrderReq);
            log.info("OrderSendParameters: {}", orderSendParameters);
        } else {
            log.warn("OrderSendParameters must be of type NewOrderReq.");
        }
    }

    @Override
    public void updateOrder(@NonNull OrderUpdateParameters orderUpdateParameters) {
        log.info("Not implemented.");
    }

    @EventListener
    public void onWebSocketSessionStatusEvent(@NonNull MexcWebSocketSessionStatusEvent event) {
        boolean isActive = isLoggedIn.get();
        if (isActive != event.isActive()) {
            isLoggedIn.set(event.isActive());
            log.info("WebSocket session is now {}", event.isActive() ? "active" : "inactive");
        }
    }

    @EventListener
    public void onMexcEvent(@NonNull MexcExchangeEvent event) {
        if (event instanceof MexcTradeEvent tradeEvent) {
            log.info("Method handleMexcEvent: MexcTradeEvent {}", tradeEvent);
            dataListeners.forEach(listener -> listener.onTrade(
                    tradeEvent.getSymbol(),
                    tradeEvent.getPrice(),
                    tradeEvent.getSize(),
                    tradeEvent.getTradeInfo()));
        } else if (event instanceof MexcDepthEvent depthEvent) {
            log.info("Method handleMexcEvent: MexcDepthEvent {}", depthEvent);
            dataListeners.forEach(listener -> listener.onDepth(
                    depthEvent.getSymbol(),
                    depthEvent.isBid(),
                    depthEvent.getPrice(),
                    depthEvent.getSize()));
        } else if (event instanceof MexcBalanceEvent balanceEvent) {
            log.info("Method handleMexcEvent: MexcBalanceEvent {}", balanceEvent);
            tradingListeners.forEach(listener -> listener.onBalance(
                    balanceEvent.getBalanceInfo()));
        } else if (event instanceof MexcExecutionEvent executionEvent) {
            log.info("Method handleMexcEvent: MexcExecutionEvent {}", executionEvent.getExecutionInfo());
            tradingListeners.forEach(listener -> listener.onOrderExecuted(
                    executionEvent.getExecutionInfo()));
        } else if (event instanceof MexcOrderInfoEvent mexcOrderInfoEvent) {
            log.info("Method handleMexcEvent: MexcOrderInfoEvent {}", mexcOrderInfoEvent.getOrderInfoUpdate());
            tradingListeners.forEach(listener -> listener.onOrderUpdated(
                    mexcOrderInfoEvent.getOrderInfoUpdate()));
        } else {
            log.warn("Method handleMexcEvent: Unsupported event type {}", event.getClass());
        }
    }


    private boolean isLoginDataValid(@NonNull LoginData loginData) {
        return loginData instanceof MexcLoginData mexcLoginData &&
                mexcLoginData.getApiKey() != null &&
                mexcLoginData.getApiSecret() != null;
    }
}
