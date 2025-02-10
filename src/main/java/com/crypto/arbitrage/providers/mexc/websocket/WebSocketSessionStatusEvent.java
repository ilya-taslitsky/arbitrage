package com.crypto.arbitrage.providers.mexc.websocket;

public class WebSocketSessionStatusEvent {
    private final boolean isActive;

    public WebSocketSessionStatusEvent(boolean isActive) {
        this.isActive = isActive;
    }

    public boolean isActive() {
        return isActive;
    }
}
