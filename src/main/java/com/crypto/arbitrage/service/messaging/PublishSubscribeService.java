package com.crypto.arbitrage.service.messaging;

import com.crypto.arbitrage.data.Topic;
import com.crypto.arbitrage.data.TopicMessage;

public interface PublishSubscribeService {
    void publish(Topic topic, TopicMessage message);
    void publish(Topic topic, TopicMessage message, boolean executeInCurrentThread);
    void subscribe(Topic topic, MessageHandler messageHandler);
    void unsubscribe(Topic topic, MessageHandler messageHandler);
}

