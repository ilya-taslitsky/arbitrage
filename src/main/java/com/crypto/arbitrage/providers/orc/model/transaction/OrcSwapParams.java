package com.crypto.arbitrage.providers.orc.model.transaction;

import java.math.BigInteger;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrcSwapParams {
  private String mint;
  private BigInteger outputAmount; // null means exact-input

  public boolean isExactInput() {
    return outputAmount == null;
  }

  public boolean isTokenAMint() {
    // Insert logic to decide if mint equals tokenMintA of the pool.
    return true;
  }

  public String getUserTokenAccountA() {
    return "UserTokenAccountAAddress";
  }

  public String getUserTokenAccountB() {
    return "UserTokenAccountBAddress";
  }
}
