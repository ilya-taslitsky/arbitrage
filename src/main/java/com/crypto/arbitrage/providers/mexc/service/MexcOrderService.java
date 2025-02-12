package com.crypto.arbitrage.providers.mexc.service;


import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crypto.arbitrage.providers.mexc.model.order.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import com.crypto.arbitrage.providers.mexc.common.MexcSignatureUtil;

import java.util.Map;
import java.time.Instant;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;


@Slf4j
@Service
public class MexcOrderService {

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String MEXC_API_KEY_HEADER = "X-MEXC-APIKEY";
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Setter
    private MexcLoginData loginData;
    private final String mexcApiUrl;

    @Autowired
    public MexcOrderService(@Value("${mexc.api.url}") String mexcApiUrl,
                            RestClient restClient,
                            ObjectMapper objectMapper) {
        this.mexcApiUrl = mexcApiUrl;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public MexcNewOrderResp sendOrder(@NonNull MexcNewOrderReq request) {
        String endpoint = "/api/v3/order";
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("symbol", request.getSymbol());
        parameters.put("side", request.getSide().name());
        parameters.put("type", request.getType().name());

        if (request.getType() == MexcOrderType.LIMIT) {
            parameters.put("quantity", request.getQuantity().toPlainString());
            parameters.put("price", request.getPrice().toPlainString());
        } else if (request.getType() == MexcOrderType.MARKET) {
            if (request.getQuantity() == null && request.getQuoteOrderQty() == null) {
                log.error("MARKET order must have either quantity or quoteOrderQty.");
            }
            if (request.getQuantity() != null) {
                parameters.put("quantity", request.getQuantity().toPlainString());
            }
            if (request.getQuoteOrderQty() != null) {
                parameters.put("quoteOrderQty", request.getQuoteOrderQty().toPlainString());
            }
        }

        if (request.getNewClientOrderId() != null && !request.getNewClientOrderId().isEmpty()) {
            parameters.put("newClientOrderId", request.getNewClientOrderId());
        }
        if (request.getRecvWindow() != null) {
            parameters.put("recvWindow", request.getRecvWindow().toString());
        }

        parameters.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));

        String signedUrl = getSignedUrl(endpoint, parameters);
        if (signedUrl == null) {
            log.error("Method sendOrder: Signed url is null.");
            return null;
        }

        try {
            ResponseEntity<String> response = restClient
                    .put()
                    .uri(signedUrl)
                    .header(MEXC_API_KEY_HEADER, loginData.getApiKey())
                    .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_APPLICATION_JSON)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().value() == 200) {
                return objectMapper.readValue(response.getBody(),
                        MexcNewOrderResp.class);

            } else {
                log.error("Error creating new Mexc order: {}", response.getBody());
            }

        } catch (IOException e) {
            Thread.currentThread().interrupt();
            log.error("Error creating new Mexc order", e);
        }
        return null;
    }

    public MexcCancelOrderResp cancelOrder(@NonNull MexcCancelOrderReq req) {
        String endpoint = "/api/v3/order";
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("symbol", req.getSymbol());

        if (req.getOrderId() != null && !req.getOrderId().isEmpty()) {
            parameters.put("orderId", req.getOrderId());
        } else {
            log.error("Either orderId or origClientOrderId must be provided.");
        }

        parameters.put("recvWindow", "5000");
        parameters.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));

        String signedUrl = getSignedUrl(endpoint, parameters);

        if (signedUrl == null) {
            log.error("Method cancelOrder: Signed url is null.");
            return null;
        }

        try {
            ResponseEntity<String> response = restClient
                    .put()
                    .uri(signedUrl)
                    .header(MEXC_API_KEY_HEADER, loginData.getApiKey())
                    .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_APPLICATION_JSON)
                    .retrieve()
                    .toEntity(String.class);
            if (response.getStatusCode().value() != 200) {
                log.error("Error canceling order: {}", response.getBody());
            }

            return objectMapper.readValue(response.getBody(), MexcCancelOrderResp.class);
        } catch (IOException e) {
            Thread.currentThread().interrupt();
            log.error("Error canceling order", e);
        }
        return null;
    }

    private String getSignedUrl(@NonNull String endpoint,
                                @NonNull Map<String, String> parameters) {
        if (mexcApiUrl == null) {
            log.error("Mexc API url is null");
            return null;
        }
        String rawQueryString = parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        String signature = MexcSignatureUtil.createSignature(loginData.getApiSecret(), rawQueryString);
        parameters.put("signature", signature);

        String encodedQueryString = parameters.entrySet().stream()
                .map(entry -> encodeValue(entry.getKey()) + "=" + encodeValue(entry.getValue()))
                .collect(Collectors.joining("&"));
        return mexcApiUrl + endpoint + "?" + encodedQueryString;
    }

    private String encodeValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
