package com.crypto.arbitrage.mexc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.crypto.arbitrage.providers.mexc.common.MexcSignatureUtil;
import com.crypto.arbitrage.providers.mexc.model.order.MexcLoginData;
import com.crypto.arbitrage.providers.mexc.model.order.MexcNewOrderReq;
import com.crypto.arbitrage.providers.mexc.model.order.MexcOrderSide;
import com.crypto.arbitrage.providers.mexc.model.order.MexcOrderType;
import com.crypto.arbitrage.providers.mexc.service.MexcOrderService;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class MexcOrderServiceTest {

  @Mock private RestClient restClient;

  // Mocks for the fluent API chain.
  @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
  @Mock private RestClient.ResponseSpec responseSpec;

  private MexcOrderService mexcOrderService;
  private final String testApiUrl = "http://test.api";
  private final String testApiKey = "testApiKey";
  private final String testApiSecret = "testApiSecret";

  @BeforeEach
  void setUp() {
    // Set up the mocked REST client chain.
    when(restClient.put()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

    // Create the order service with the test API URL and mocked RestClient.
    mexcOrderService = new MexcOrderService(testApiUrl, restClient);
    MexcLoginData loginData = new MexcLoginData();
    loginData.setApiKey(testApiKey);
    loginData.setApiSecret(testApiSecret);
    mexcOrderService.setLoginData(loginData);
  }

  /**
   * This test verifies that sendOrder triggers the full REST client chain and that the URL built
   * contains all the expected parameters.
   */
  @Test
  void testSendOrder_chainTriggeredAndRestClientCalled() {
    // Create a dummy MARKET order (for MARKET orders, either quantity or quoteOrderQty must be
    // set).
    MexcNewOrderReq orderReq = new MexcNewOrderReq();
    orderReq.setSymbol("BTCUSDT");
    orderReq.setSide(MexcOrderSide.BUY);
    orderReq.setType(MexcOrderType.MARKET);
    orderReq.setQuantity(10);
    orderReq.setQuoteOrderQty(0);
    orderReq.setNewClientOrderId(null);
    orderReq.setRecvWindow(5000);

    // Act: call sendOrder.
    mexcOrderService.sendOrder(orderReq);

    // Capture the URL passed to the REST client.
    ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestBodyUriSpec, times(1)).uri(uriCaptor.capture());
    String capturedUrl = uriCaptor.getValue();

    // Assert: the URL should include the endpoint and expected parameters.
    assertThat(capturedUrl).contains("/api/v3/order");
    assertThat(capturedUrl).contains("symbol=BTCUSDT");
    assertThat(capturedUrl).contains("side=BUY");
    assertThat(capturedUrl).contains("type=MARKET");
    assertThat(capturedUrl).contains("quantity=10");
    assertThat(capturedUrl).contains("recvWindow=5000");
    assertThat(capturedUrl).contains("timestamp=");
    assertThat(capturedUrl).contains("signature=");

    // Verify that headers are set correctly.
    verify(requestBodyUriSpec).header("X-MEXC-APIKEY", testApiKey);
    verify(requestBodyUriSpec).header("Content-Type", "application/json");

    // Verify that the REST client's retrieve() method was called.
    verify(requestBodyUriSpec, times(1)).retrieve();
  }

  /**
   * This test verifies that the signature appended to the URL is valid. It parses the query string,
   * reconstructs the raw query string that was signed, and then compares the computed signature
   * with the one in the URL.
   */
  @Test
  void testSignatureValidity() throws Exception {
    // Create a dummy LIMIT order.
    MexcNewOrderReq orderReq = new MexcNewOrderReq();
    orderReq.setSymbol("ETHUSDT");
    orderReq.setSide(MexcOrderSide.SELL);
    orderReq.setType(MexcOrderType.LIMIT);
    orderReq.setQuantity(5);
    orderReq.setPrice(2000);
    orderReq.setNewClientOrderId("order123");
    orderReq.setRecvWindow(6000); // Non-zero so recvWindow will be added as constant "10000"

    // Act: call sendOrder.
    mexcOrderService.sendOrder(orderReq);

    // Capture the URL passed to the REST client.
    ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
    verify(requestBodyUriSpec).uri(uriCaptor.capture());
    String capturedUrl = uriCaptor.getValue();

    // Parse the URL to extract the query string.
    URI uri = new URI(capturedUrl);
    String query = uri.getQuery(); // The encoded query string.

    // Split the query string into key=value pairs (preserving order).
    String[] pairs = query.split("&");
    // Use a LinkedHashMap to preserve the insertion order.
    LinkedHashMap<String, String> params = new LinkedHashMap<>();
    for (String pair : pairs) {
      String[] kv = pair.split("=", 2);
      String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
      String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
      params.put(key, value);
    }

    // Remove the signature parameter from the map and store its value.
    String capturedSignature = params.remove("signature");

    // Reconstruct the raw query string that was signed (i.e. without the signature).
    String rawQueryString =
        params.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));

    // Compute the expected signature using the same secret and raw query string.
    String expectedSignature = MexcSignatureUtil.createSignature(testApiSecret, rawQueryString);

    // Assert that the captured signature matches the expected signature.
    assertThat(capturedSignature).isEqualTo(expectedSignature);
  }
}
