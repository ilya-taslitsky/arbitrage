package com.crypto.arbitrage.providers.mexc.model.common;


import com.crypto.arbitrage.providers.mexc.model.MexcData;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MexcSubscriptionResp implements MexcData {
    @JsonProperty("id")
    private int id;
    @JsonProperty("code")
    private int code;
    @JsonProperty("msg")
    private String msg;
}

