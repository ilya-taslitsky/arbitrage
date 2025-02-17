package com.crypto.arbitrage.providers.mexc.model.instrument;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MexcUnsubscribedInstrumentEvent implements MexcInstrumentEvent {
    private String symbol;
}
