package com.crypto.arbitrage.providers.mexc.model.withdraw;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WithdrawResponse {
    private String id;
}
