package com.crypto.arbitrage.providers.mexc.service.parser;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.crypto.arbitrage.providers.mexc.model.depth.DepthData;
import com.crypto.arbitrage.providers.mexc.model.trade.TradeStream;


@Slf4j
@Component
@RequiredArgsConstructor
public class MexcMessageDispatcher {

    private static final String DEAL_CHANNEL = "public.deals.v3.api";
    private static final String DEPTH_CHANNEL = "public.limit.depth.v3.api";

    private final ObjectMapper objectMapper;
    private final MexcTradeMessageProcessor tradeProcessor;
    private final MexcDepthMessageProcessor depthProcessor;

    public void dispatchMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);

            if (isChannelMessage(root)) {
                processChannelMessage(root, message);
            } else if (isControlMessage(root)) {
                processControlMessage(root, message);
            } else {
                processUnrecognizedMessage(root, message);
            }
        } catch (Exception e) {
            log.error("Error dispatching message: {}", message, e);
        }
    }

    private boolean isChannelMessage(JsonNode root) {
        return root.hasNonNull("c") && !root.get("c").asText().isEmpty();
    }

    private boolean isControlMessage(JsonNode root) {
        return root.has("method");
    }

    private void processChannelMessage(JsonNode root, String message) {
        String channel = root.get("c").asText();
        String symbol = root.path("s").asText();

        if (!channel.contains("@")) {
            log.debug("Channel does not contain '@': {}", message);
            return;
        }

        String[] parts = channel.split("@");
        if (parts.length < 2) {
            log.debug("Channel split result has less than 2 parts: {}", message);
            return;
        }

        String identifier = parts[1];
        switch (identifier) {
            case DEAL_CHANNEL -> {
                TradeStream tradeStream;
                try {
                    tradeStream = objectMapper.treeToValue(root, TradeStream.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                tradeProcessor.process(tradeStream);
            }
            case DEPTH_CHANNEL -> {
                DepthData depthData;
                try {
                    depthData = objectMapper.treeToValue(root.path("d"), DepthData.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                depthProcessor.process(symbol, depthData);
            }
            default -> log.warn("Unrecognized channel identifier: {} in message: {}", identifier, message);
        }
    }

    private void processControlMessage(JsonNode root, String message) {
        String method = root.get("method").asText();
        log.debug("Received control message with method '{}': {}", method, message);
    }

    private void processUnrecognizedMessage(JsonNode root, String message) {
        log.debug("Received unrecognized message structure: {}", message);
    }
}

