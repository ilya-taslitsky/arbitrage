package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "bot")
public class Bot {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ACTIVE")
    private Boolean active;
    @ManyToOne
    @JoinColumn(name = "DEX_PAIR_ID")
    private CurrencyToCurrency dexPairId;
    @ManyToOne
    @JoinColumn(name = "CEX_PAIR_ID")
    private CurrencyToCurrency cexPairId;
    @ManyToOne
    @JoinColumn(name = "EXCHANGE_PAIR_ID")
    private DexToDatasource exchangePairId;
    @Column(name = "TRADING_AMOUNT")
    private BigDecimal tradingAmount;
    @Column(name = "PROFIT_PERCENT")
    private Double profitPercent;
    @Column(name = "SLIPPAGE_PERCENT")
    private Double slippagePercent;
    @OneToOne
    @JoinColumn(name = "CRYPTO_WALLET_ID")
    private CryptoWallet cryptoWallet;
    @OneToOne
    @JoinColumn(name = "CEX_ACCOUNT_ID")
    private CexAccount cexAccount;
    @OneToOne
    @JoinColumn(name = "BLOCKCHAIN_ID")
    private Blockchain blockchain;
}
