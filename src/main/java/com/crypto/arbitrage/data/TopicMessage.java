package com.crypto.arbitrage.data;

import lombok.Data;

@Data
public class TopicMessage {
    private String userId;
    private Object message;
    private volatile boolean isProcessed;

    public TopicMessage(String userId, Object message) {
        this.userId = userId;
        this.message = message;
    }

    public boolean tryProcess() {
        if (!isProcessed) {
            synchronized (this) {
                if (!isProcessed) {
                    isProcessed = true;
                    return true;
                }
            }
        }
        return false;
    }
}
