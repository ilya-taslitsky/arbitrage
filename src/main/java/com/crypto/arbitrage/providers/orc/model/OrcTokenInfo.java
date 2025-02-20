package com.crypto.arbitrage.providers.orc.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrcTokenInfo {
  private String address;
  private String programId;
  private String imageUrl;
  private String symbol;
  private String name;
  private int decimals;
  private List<String> tags;
}
