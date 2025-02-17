package com.crypto.arbitrage.providers.mexc.model.account;

import com.crypto.arbitrage.providers.mexc.model.event.MexcExchangeEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import velox.api.layer1.data.BalanceInfo;

@Data
@AllArgsConstructor
public class MexcBalanceEvent implements MexcExchangeEvent  {
    private BalanceInfo balanceInfo;

}
