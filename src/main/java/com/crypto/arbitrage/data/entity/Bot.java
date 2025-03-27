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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Boolean active;
    @ManyToOne
    @JoinColumn(name = "dex_pair_id")
    private CurrencyToCurrency dexPairId;
    @ManyToOne
    @JoinColumn(name = "cex_pair_id")
    private CurrencyToCurrency cexPairId;
    @ManyToOne
    @JoinColumn(name = "exchange_pair_id")
    private DexToDatasource exchangePairId;
    @Column(name = "trading_amount")
    private BigDecimal tradingAmount;
    @Column(name = "profit_percent")
    private Double profitPercent;
    @Column(name = "slippage_percent")
    private Double slippagePercent;
    @OneToOne
    @JoinColumn(name = "crypto_wallet_id")
    private CryptoWallet cryptoWallet;
    @OneToOne
    @JoinColumn(name = "cex_account_id")
    private CexAccount cexAccount;
    @OneToOne
    @JoinColumn(name = "blockchain_id")
    private Blockchain blockchain;
}
