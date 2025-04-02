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

  private String cex;
  private String dex;
  private Double profit;

  @Column(name = "trading_amount")
  private BigDecimal tradingAmount;

  @Column(name = "profit_percent")
  private Double profitPercent;

  @Column(name = "cex_pair", nullable = false)
  private String cexPair;

  @Column(name = "dex_pair", nullable = false)
  private String dexPair;

  @Column(name = "blockchain", nullable = false)
  private String blockchain;

  private LocalDateTime date;
}
