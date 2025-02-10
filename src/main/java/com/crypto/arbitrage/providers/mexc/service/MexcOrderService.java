package com.crypto.arbitrage.providers.mexc.service;


import com.crypto.arbitrage.providers.mexc.common.SignatureUtil;
import com.crypto.arbitrage.providers.mexc.config.MexcConfig;
import com.crypto.arbitrage.providers.mexc.model.order.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class MexcOrderService {

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String MEXC_API_KEY_HEADER = "X-MEXC-APIKEY";
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

    private final MexcConfig mexcConfig;
    private final RestClient restClient;
    private final MexcLoginData loginData;
    private final ObjectMapper objectMapper;

    @Autowired
    public MexcOrderService(MexcConfig mexcConfig,
                            RestClient restClient,
                            MexcConfig mexcClientConfig,
                            ObjectMapper objectMapper) {
        this.mexcConfig = mexcConfig;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.loginData = mexcClientConfig.getLoginData();
    }

    public NewOrderResp sendOrder(@NonNull NewOrderReq request) {
        String endpoint = "/api/v3/order";
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("symbol", request.getSymbol());
        parameters.put("side", request.getSide().name());
        parameters.put("type", request.getType().name());

        if (request.getType() == OrderType.LIMIT) {
            if (request.getQuantity() == null || request.getPrice() == null) {
                log.error("LIMIT order must have quantity and price.");
            }
            parameters.put("quantity", request.getQuantity().toPlainString());
            parameters.put("price", request.getPrice().toPlainString());
        } else if (request.getType() == OrderType.MARKET) {
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
        if(isSignedUrlNull(signedUrl)) {
            return null;
        }

        try {
            assert signedUrl != null;
            ResponseEntity<String> response = restClient
                    .put()
                    .uri(signedUrl)
                    .header(MEXC_API_KEY_HEADER, loginData.getApiKey())
                    .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_APPLICATION_JSON)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().value() == 200) {
                return objectMapper.readValue(response.getBody(),
                        NewOrderResp.class);

            } else {
                log.error("Error creating new Mexc order: {}", response.getBody());
            }

        } catch (IOException e) {
            Thread.currentThread().interrupt();
            log.error("Error creating new Mexc order", e);
        }
        return null;
    }

    public CancelOrderResp cancelOrder(@NonNull CancelOrderReq req) {
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

        if(isSignedUrlNull(signedUrl)) {
            return null;
        }

        try {
            assert signedUrl != null;
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

            return objectMapper.readValue(response.getBody(), CancelOrderResp.class);
        } catch (IOException e) {
            Thread.currentThread().interrupt();
            log.error("Error canceling order", e);
        }
        return null;
    }

    private String getSignedUrl(@NonNull String endpoint,
                                @NonNull Map<String, String> parameters) {
        String apiUrl = mexcConfig.getApiUrl();
        if(apiUrl == null) {
            log.error("Mexc API url is null");
            return null;
        }
        String rawQueryString = parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        String signature = SignatureUtil.createSignature(loginData.getApiSecret(), rawQueryString);
        parameters.put("signature", signature);

        String encodedQueryString = parameters.entrySet().stream()
                .map(entry -> encodeValue(entry.getKey()) + "=" + encodeValue(entry.getValue()))
                .collect(Collectors.joining("&"));
        return apiUrl + endpoint + "?" + encodedQueryString;
    }

    private boolean isSignedUrlNull(String signedUrl) {
        if(signedUrl != null){
            return false;
        }else {
            log.error("Signed url is null");
            return true;
        }
    }

    private String encodeValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
