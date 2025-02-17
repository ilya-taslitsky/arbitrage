package com.crypto.arbitrage.providers.mexc.model.instrument;

import lombok.AllArgsConstructor;
import lombok.Getter;
import velox.api.layer1.data.InstrumentInfo;

@Getter
@AllArgsConstructor
public class MexcSubscribedInstrumentEvent implements MexcInstrumentEvent {
    private InstrumentInfo instrumentInfo;
}
