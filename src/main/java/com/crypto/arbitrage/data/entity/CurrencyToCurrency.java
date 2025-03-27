package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "currency_to_currency")
public class CurrencyToCurrency {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "BASE_CURRENCY_ID", nullable = false)
    private Currency baseCurrency;

    @ManyToOne
    @JoinColumn(name = "QUOTE_CURRENCY_ID", nullable = false)
    private Currency quoteCurrency;
}

