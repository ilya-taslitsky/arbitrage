package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "cex_crypto_balance")
public class CexCryptoBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "CURRENCY_ID", nullable = false)
    private Currency currency;
    @Column(name = "BALANCE")
    private BigDecimal balance;
    @ManyToOne
    @JoinColumn(name = "CEX_ACCOUNT_ID")
    private CexAccount cexAccount;
}
