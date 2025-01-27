package com.crypto.arbitrage.service.impl;

import com.crypto.arbitrage.data.Topic;
import com.crypto.arbitrage.data.TopicMessage;
import com.crypto.arbitrage.service.messaging.MessageHandler;
import com.crypto.arbitrage.service.messaging.PublishSubscribeService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class PublishSubscribeServiceImpl implements PublishSubscribeService  {
    private final Map<String, List<MessageHandler>> subscribers;
    private final Map<Topic, ExecutorService> executorServiceMap = new HashMap<>();


    public PublishSubscribeServiceImpl() {
        this.subscribers = new ConcurrentHashMap<>();

        // This is a loop that iterates over the values of the Topic enum and creates a new single-threaded executor for each value.
        for (Topic topic : Topic.values()) {
            executorServiceMap.put(topic, Executors.newSingleThreadScheduledExecutor());
        }
    }

    @Override
    public void publish(Topic topic, TopicMessage message) {
        publish(topic.toString(), message, false);
    }

    @Override
    public void subscribe(Topic topic, MessageHandler messageHandler) {
        subscribe(topic.toString(), messageHandler);
    }

    private void publish(String topic, TopicMessage message, boolean executeInCurrentThread) {
        List<MessageHandler> handlers = subscribers.get(topic);
//        if (!(message.getMessage() instanceof ExecutionRequest)) {
//            log.info("Message is published to topic '" + topic + "': " + message.getMessage());
//        }
        if (handlers != null) {
            for (MessageHandler handler : handlers) {
                executeMessage(topic, message, handler, executeInCurrentThread);
            }
        }
    }

    @Override
    public void publish(Topic topic, TopicMessage message, boolean executeInCurrentThread) {
        publish(topic.toString(), message, executeInCurrentThread);
    }

    private void subscribe(String topic, MessageHandler messageHandler) {
        subscribers.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(messageHandler);
    }

    @Override
    public void unsubscribe(Topic topic, MessageHandler messageHandler) {
        List<MessageHandler> handlers = subscribers.get(topic.toString());
        if (handlers != null) {
            handlers.remove(messageHandler);
        }
    }


    private void executeMessage(String topic, TopicMessage message, MessageHandler handler, boolean executeInCurrentThread) {
        Runnable task = () -> execute(topic, message, handler);
        if (executeInCurrentThread) {
            task.run();
        } else {
            getExecutorService(Topic.valueOf(topic)).execute(task);
        }
    }

    private void execute(String topic, TopicMessage message, MessageHandler handler) {
        try {
            handler.handleMessage(message);
        } catch (Exception ex) {
            log.error("Error while handling the message in topic: {} message: {}", topic, ex.getMessage(), ex);
        }
    }

    private ExecutorService getExecutorService(Topic topic) {
        return executorServiceMap.get(topic);
    }


    @PreDestroy
    public void destroy() {
        executorServiceMap.values().forEach(ExecutorService::shutdown);
    }
}
