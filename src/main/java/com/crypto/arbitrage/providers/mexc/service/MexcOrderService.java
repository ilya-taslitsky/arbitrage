package com.crypto.arbitrage.providers.mexc.service;

import com.crypto.arbitrage.providers.mexc.common.MexcSignatureUtil;
import com.crypto.arbitrage.providers.mexc.model.order.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class MexcOrderService {

  private static final String RECOVERY_WINDOW = "10000";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String MEXC_API_KEY_HEADER = "X-MEXC-APIKEY";
  private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

  private final RestClient restClient;

  @Setter private MexcLoginData loginData;
  private final String mexcApiUrl;

  @Autowired
  public MexcOrderService(@Value("${mexc.api.url}") String mexcApiUrl, RestClient restClient) {
    this.mexcApiUrl = mexcApiUrl;
    this.restClient = restClient;
  }

  public void sendOrder(@NonNull MexcNewOrderReq request) {
    String endpoint = "/api/v3/order";
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("symbol", request.getSymbol());
    parameters.put("side", request.getSide().name());
    parameters.put("type", request.getType().name());

    if (request.getType() == MexcOrderType.LIMIT) {
      parameters.put("quantity", String.valueOf(request.getQuantity()));
      parameters.put("price", String.valueOf(request.getPrice()));
    } else if (request.getType() == MexcOrderType.MARKET) {
      if (request.getQuantity() == 0 && request.getQuoteOrderQty() == 0) {
        log.error("Method sendOrder: MARKET order must have either quantity or quoteOrderQty.");
        return;
      }
      if (request.getQuantity() != 0) {
        parameters.put("quantity", String.valueOf(request.getQuantity()));
      }
      if (request.getQuoteOrderQty() != 0) {
        parameters.put("quoteOrderQty", String.valueOf(request.getQuoteOrderQty()));
      }
    }

    if (request.getNewClientOrderId() != null && !request.getNewClientOrderId().isEmpty()) {
      parameters.put("newClientOrderId", request.getNewClientOrderId());
    }
    if (request.getRecvWindow() != 0) {
      parameters.put("recvWindow", String.valueOf(request.getRecvWindow()));
    } else {
      parameters.put("recvWindow", RECOVERY_WINDOW);
    }

    parameters.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));

    String signedUrl = getSignedUrl(endpoint, parameters);
    if (signedUrl == null) {
      log.error("Method sendOrder: Signed url is null.");
      return;
    }
    restClient
        .put()
        .uri(signedUrl)
        .header(MEXC_API_KEY_HEADER, loginData.getApiKey())
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_APPLICATION_JSON)
        .retrieve();
  }

  public void cancelOrder(@NonNull MexcCancelOrderReq req) {
    String endpoint = "/api/v3/order";
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("symbol", req.getSymbol());

    if (req.getOrderId() != null && !req.getOrderId().isEmpty()) {
      parameters.put("orderId", req.getOrderId());
    } else {
      log.error("Either orderId or origClientOrderId must be provided.");
    }

    parameters.put("recvWindow", RECOVERY_WINDOW);
    parameters.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));

    String signedUrl = getSignedUrl(endpoint, parameters);

    if (signedUrl == null) {
      log.error("Method cancelOrder: Signed url is null.");
      return;
    }

    restClient
        .put()
        .uri(signedUrl)
        .header(MEXC_API_KEY_HEADER, loginData.getApiKey())
        .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_APPLICATION_JSON)
        .retrieve();
  }

  private String getSignedUrl(@NonNull String endpoint, @NonNull Map<String, String> parameters) {
    if (mexcApiUrl == null) {
      log.error("Mexc API url is null");
      return null;
    }
    String rawQueryString =
        parameters.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));

    String signature = MexcSignatureUtil.createSignature(loginData.getApiSecret(), rawQueryString);
    parameters.put("signature", signature);

    String encodedQueryString =
        parameters.entrySet().stream()
            .map(entry -> encodeValue(entry.getKey()) + "=" + encodeValue(entry.getValue()))
            .collect(Collectors.joining("&"));
    return mexcApiUrl + endpoint + "?" + encodedQueryString;
  }

  private String encodeValue(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
