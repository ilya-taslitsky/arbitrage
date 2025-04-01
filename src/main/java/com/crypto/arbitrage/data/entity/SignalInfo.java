package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "signal_info")
public class SignalInfo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private LocalDateTime date;

  @ManyToOne
  @JoinColumn(name = "cex_id", nullable = false)
  private Cex cex;

  @ManyToOne
  @JoinColumn(name = "dex_id", nullable = false)
  private Dex dex;

  private Double profit;

  @Column(name = "trading_amount")
  private BigDecimal tradingAmount;

  @Column(name = "profit_percent")
  private Double profitPercent;

  @ManyToOne
  @JoinColumn(name = "cex_pair_id", nullable = false)
  private CurrencyToCurrency cexPair;

  @ManyToOne
  @JoinColumn(name = "dex_pair_id", nullable = false)
  private CurrencyToCurrency dexPair;

  @ManyToOne
  @JoinColumn(name = "blockchain_id", nullable = false)
  private Blockchain blockchain;
}
