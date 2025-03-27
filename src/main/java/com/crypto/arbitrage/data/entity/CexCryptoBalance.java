package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "cex_crypto_balance")
public class CexCryptoBalance {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "currency_id", nullable = false)
  private Currency currency;

  private BigDecimal balance;

  @ManyToOne
  @JoinColumn(name = "cex_account_id")
  private CexAccount cexAccount;
}
