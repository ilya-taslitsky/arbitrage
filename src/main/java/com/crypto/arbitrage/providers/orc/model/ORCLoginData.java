package com.crypto.arbitrage.providers.orc.model;

import lombok.*;
import velox.api.layer1.data.LoginData;

@Getter
@Setter
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ORCLoginData implements LoginData {
  private String clusterUrl;
  private String walletPrivateKey;
}
