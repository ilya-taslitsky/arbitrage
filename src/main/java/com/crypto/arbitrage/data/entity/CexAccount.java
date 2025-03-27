package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "cex_account")
public class CexAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String exchange;

  @OneToMany(mappedBy = "cexAccount")
  private Set<CexCryptoBalance> cexCryptoBalance;

  @Transient private String publicKey;
  @Transient private String privateKey;
}
