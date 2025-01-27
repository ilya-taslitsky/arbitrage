package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "datasources")
@Data
public class DatasourceInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private boolean active;
    private String symbol; // symbol that user sees
    private String exchange; // exchange name that user sees
    private String type; // type exchange that users sees
    @Column(name = "exchange_port_alias")
    private String exchangePortAlias; // alias that we use to get provider via exchange port
    @Column(name = "exchange_port_type")
    private String exchangePortType; // type that we use to get provider via exchange port
    @Column(name = "exchange_port_exchange")
    private String exchangePortExchange; // exchange that we use to get provider via exchange port
    private String alias;
    @Column(name = "pips_size")
    private double pipsSize;
    @Column(name = "size_multiplier")
    private double sizeMultiplier;
    @Column(name = "price_multiplier")
    private double priceMultiplier;
    @Column(name = "commission_fee")
    private double commissionFee;
}