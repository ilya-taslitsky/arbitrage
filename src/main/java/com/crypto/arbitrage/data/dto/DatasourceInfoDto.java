package com.crypto.arbitrage.data.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DatasourceInfoDto {

    private Long id;

    @NotEmpty(message = "Symbol is required")
    private String symbol;

    private String exchange;
    private String type;

    private String alias;

    private String exchangePortAlias;

    private String exchangePortExchange;
    private String exchangePortType;

    private boolean active;

    @NotNull(message = "Pips size is required")
    private Double pipsSize;

    @NotNull(message = "Size multiplier is required")
    private Double sizeMultiplier;

    private Double priceMultiplier;

    private Double commissionFee;
}