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

  @Column(name = "cex_pair", nullable = false)
  private String cexPair;

  @Column(name = "dex_pair", nullable = false)
  private String dexPair;

  @Column(name = "direction_dex")
  private Direction directionDex;

  private String blockchain;

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

  private LocalDateTime date;
}
