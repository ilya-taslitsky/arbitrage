package com.crypto.arbitrage.providers.mexc.model.order;

import lombok.Getter;
import lombok.Setter;
import velox.api.layer1.data.LoginData;

@Getter
@Setter
public class MexcLoginData implements LoginData  {
    private String apiKey;
    private String apiSecret;
}
