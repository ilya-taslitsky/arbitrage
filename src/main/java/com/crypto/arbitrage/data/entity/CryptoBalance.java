package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "crypto_balance")
public class CryptoBalance {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "CURRENCY_ID", nullable = false)
    private Currency currency;
    @Column(name = "BALANCE")
    private BigDecimal balance;
    @ManyToOne
    @JoinColumn(name = "CRYPTO_WALLET_ID")
    private CryptoWallet cryptoWallet;
}
