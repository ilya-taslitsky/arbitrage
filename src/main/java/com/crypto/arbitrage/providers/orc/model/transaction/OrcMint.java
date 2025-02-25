package com.crypto.arbitrage.providers.orc.model.transaction;

import java.math.BigInteger;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrcMint {
  public String address;
  public BigInteger supply;
  public int decimals;
  public boolean isInitialized;
  // Additional fields (mintAuthority, freezeAuthority) can be added here.
}
