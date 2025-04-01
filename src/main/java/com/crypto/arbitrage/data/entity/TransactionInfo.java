package com.crypto.arbitrage.data.entity;

import com.crypto.arbitrage.data.Direction;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "transaction_info")
public class TransactionInfo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "transaction_id")
  private String transactionId;

  private LocalDateTime date;

  @ManyToOne
  @JoinColumn(name = "cex_pair_id", nullable = false)
  private CurrencyToCurrency cexPair;

  @ManyToOne
  @JoinColumn(name = "dex_pair_id", nullable = false)
  private CurrencyToCurrency dexPair;

  @Column(name = "direction_dex")
  private Direction directionDex;

  @ManyToOne
  @JoinColumn(name = "blockchain_id")
  private Blockchain blockchain;

  @Column(name = "sum_bought")
  private BigDecimal sumBought;

  @Column(name = "sum_sold")
  private BigDecimal sumSold;

  private BigDecimal profit;

  @Column(name = "profit_percent")
  private Double profitPercent;

  @Column(name = "cex_fee")
  private Double cexFee;

  @Column(name = "dex_fee")
  private Double dexFee;
}
