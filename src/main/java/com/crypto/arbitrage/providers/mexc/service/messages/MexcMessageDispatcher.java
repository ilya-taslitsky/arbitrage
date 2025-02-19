package com.crypto.arbitrage.providers.mexc.service.messages;

import com.crypto.arbitrage.providers.mexc.model.account.MexcAccountBalance;
import com.crypto.arbitrage.providers.mexc.model.common.MexcSubscriptionResp;
import com.crypto.arbitrage.providers.mexc.model.depth.BookDepthResponse;
import com.crypto.arbitrage.providers.mexc.model.order.MexcExecutionInfo;
import com.crypto.arbitrage.providers.mexc.model.order.MexcOrderResponse;
import com.crypto.arbitrage.providers.mexc.model.trade.MexcTradeStream;
import com.crypto.arbitrage.service.messaging.PublishSubscribeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MexcMessageDispatcher {
  private final PublishSubscribeService publishSubscribeService;

  private static final String DEAL_CHANNEL = "public.deals.v3.api";
  private static final String DEPTH_CHANNEL = "public.limit.depth.v3.api";
  private static final String BALANCE_UPDATES_CHANNEL = "private.account.v3.api";
  private static final String ORDER_UPDATES_CHANNEL = "private.orders.v3.api";
  private static final String ACCOUNT_DEALS_CHANNEL = "private.deals.v3.api";

  private final ObjectMapper objectMapper;
  private final MexcDataProcessor dataProcessor;

  public void dispatchMessage(@NonNull String message) {
    try {
      JsonNode root = objectMapper.readTree(message);

      if (isChannelMessage(root)) {
        processChannelMessage(root, message);
      } else if (isSubscriptionMessage(root)) {
        processSubscriptionMessage(root);
      } else {
        processUnrecognizedMessage(message);
      }
    } catch (Exception e) {
      log.error("Error dispatching message: {}", message, e);
    }
  }

  private boolean isChannelMessage(@NonNull JsonNode root) {
    return root.hasNonNull("c") && !root.get("c").asText().isEmpty();
  }

  private boolean isSubscriptionMessage(@NonNull JsonNode root) {
    return root.has("id") && root.has("code") && root.has("msg");
  }

  private void processChannelMessage(@NonNull JsonNode root, @NonNull String message) {
    String channel = root.get("c").asText();
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
        MexcTradeStream mexcTradeStream;
        try {
          mexcTradeStream = objectMapper.treeToValue(root, MexcTradeStream.class);
        } catch (JsonProcessingException e) {
          log.error("Error parsing trade stream: {}", message, e);
          throw new RuntimeException(e);
        }
        dataProcessor.process(mexcTradeStream);
      }
      case DEPTH_CHANNEL -> {
        BookDepthResponse bookDepthResponse;
        try {
          bookDepthResponse = objectMapper.treeToValue(root, BookDepthResponse.class);
        } catch (JsonProcessingException e) {
          log.error("Error parsing depth data: {}", message, e);
          throw new RuntimeException(e);
        }
        dataProcessor.process(bookDepthResponse);
      }
      case BALANCE_UPDATES_CHANNEL -> {
        MexcAccountBalance mexcAccountBalance;
        try {
          mexcAccountBalance = objectMapper.treeToValue(root, MexcAccountBalance.class);
        } catch (JsonProcessingException e) {
          log.error("Error parsing account balance: {}", message, e);
          throw new RuntimeException(e);
        }
        dataProcessor.process(mexcAccountBalance);
      }
      case ORDER_UPDATES_CHANNEL -> {
        MexcOrderResponse mexcOrderResponse;
        try {
          mexcOrderResponse = objectMapper.treeToValue(root, MexcOrderResponse.class);
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
        dataProcessor.process(mexcOrderResponse);
      }
      case ACCOUNT_DEALS_CHANNEL -> {
        MexcExecutionInfo mexcExecutionInfo;
        try {
          mexcExecutionInfo = objectMapper.treeToValue(root, MexcExecutionInfo.class);
        } catch (JsonProcessingException e) {
          throw new RuntimeException(e);
        }
        dataProcessor.process(mexcExecutionInfo);
      }
      default ->
          log.warn("Unrecognized channel identifier: {} in message: {}", identifier, message);
    }
  }

  private void processSubscriptionMessage(@NonNull JsonNode root) {
    try {
      MexcSubscriptionResp subscriptionResp =
          objectMapper.treeToValue(root, MexcSubscriptionResp.class);
      if (subscriptionResp != null) {
        dataProcessor.process(subscriptionResp);
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private void processUnrecognizedMessage(@NonNull String message) {
    log.info("Received unrecognized message structure: {}", message);
  }
}
