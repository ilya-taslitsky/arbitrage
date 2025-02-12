package com.crypto.arbitrage.providers.mexc.model.event;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class MexcSubscriptionEvent implements MexcExchangeEvent {
    private String channel;
}
