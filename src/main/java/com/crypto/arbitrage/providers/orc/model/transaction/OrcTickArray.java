package com.crypto.arbitrage.providers.orc.model.transaction;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrcTickArray {
  public String address;
  public int startTickIndex;
  public OrcTick[] ticks; // e.g. array of 88 ticks
  public boolean exists;
  public String programAddress;
}
