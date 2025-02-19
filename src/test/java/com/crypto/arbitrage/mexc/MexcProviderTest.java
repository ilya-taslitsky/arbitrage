package com.crypto.arbitrage.mexc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.crypto.arbitrage.providers.mexc.MexcProvider;
import com.crypto.arbitrage.providers.mexc.common.MexcMapper;
import com.crypto.arbitrage.providers.mexc.model.order.MexcNewOrderReq;
import com.crypto.arbitrage.providers.mexc.model.order.MexcOrderSide;
import com.crypto.arbitrage.providers.mexc.model.order.MexcOrderType;
import com.crypto.arbitrage.providers.mexc.service.MexcOrderService;
import com.crypto.arbitrage.providers.mexc.websocket.MexcWebSocketManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import velox.api.layer1.data.OrderDuration;
import velox.api.layer1.data.OrderSendParameters;
import velox.api.layer1.data.SimpleOrderSendParameters;

@ExtendWith(MockitoExtension.class)
class MexcProviderTest {

  @Mock private MexcOrderService mexcOrderService;

  @Mock private ApplicationEventPublisher publisher;

  @Mock private MexcWebSocketManager mexcWebSocketManager;

  private MexcProvider mexcProvider;

  @BeforeEach
  void setUp() {
    mexcProvider = new MexcProvider(mexcOrderService, publisher, mexcWebSocketManager);
  }

  /**
   * Test that when a valid SimpleOrderSendParameters is passed, the order is converted using
   * MexcMapper.toMexcNewOrderReq and then passed to mexcOrderService.sendOrder.
   */
  @Test
  void testSendOrder_withValidSimpleOrderSendParameters() {
    OrderSendParameters orderSendParameters =
        new SimpleOrderSendParameters("BTCUSDT", true, 10, OrderDuration.DAY, 10.00, 10.00);
    MexcNewOrderReq actualNewOrderReq =
        MexcMapper.toMexcNewOrderReq((SimpleOrderSendParameters) orderSendParameters);
    MexcNewOrderReq expectedNewOrderReq =
        MexcNewOrderReq.builder()
            .symbol("BTCUSDT")
            .side(MexcOrderSide.BUY)
            .type(MexcOrderType.MARKET)
            .quantity(10)
            .build();

    // Act: call the method under test.
    mexcProvider.sendOrder(orderSendParameters);

    // Assert: verify that the order service was called with the expected request.
    verify(mexcOrderService, times(1)).sendOrder(actualNewOrderReq);
    assertThat(actualNewOrderReq).isEqualTo(expectedNewOrderReq);
  }

  /** Test that when an unsupported OrderSendParameters type is passed, no order is sent. */
  @Test
  void testSendOrder_withUnsupportedOrderSendParameters() {
    // Arrange: create a dummy unsupported OrderSendParameters instance.
    OrderSendParameters unsupportedParams = new DummyUnsupportedOrderSendParameters();

    // Act: call sendOrder with an unsupported type.
    mexcProvider.sendOrder(unsupportedParams);

    // Assert: verify that mexcOrderService.sendOrder is never called.
    verifyNoInteractions(mexcOrderService);
  }

  /** Dummy class for an unsupported OrderSendParameters implementation. */
  private static class DummyUnsupportedOrderSendParameters implements OrderSendParameters {}
}
