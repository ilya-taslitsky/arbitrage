package com.crypto.arbitrage.service.messaging;

import com.crypto.arbitrage.data.TopicMessage;

public interface MessageHandler {

    void handleMessage(TopicMessage message);
}
