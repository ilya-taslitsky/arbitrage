package com.crypto.arbitrage.providers.mexc.model.order;

import com.crypto.arbitrage.providers.mexc.model.event.MexcExchangeEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import velox.api.layer1.data.ExecutionInfo;

@Getter
@AllArgsConstructor
public class MexcExecutionEvent implements MexcExchangeEvent {
    private ExecutionInfo executionInfo;
}
