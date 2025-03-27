package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "crypto_wallet")
public class CryptoWallet {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne
  @JoinColumn(name = "blockchain_id", nullable = false)
  private Blockchain blockchain;

  @OneToMany(mappedBy = "cryptoWallet")
  private Set<CryptoBalance> cryptoBalance;

  @Transient private String privateKey;
}
