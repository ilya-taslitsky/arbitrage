package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "crypto_balance")
public class CryptoBalance {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "currency_id", nullable = false)
  private Currency currency;

  private BigDecimal balance;

  @ManyToOne
  @JoinColumn(name = "crypto_wallet_id")
  private CryptoWallet cryptoWallet;
}
