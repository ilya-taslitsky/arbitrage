package com.crypto.arbitrage.data;

public enum Direction {
  BUY("Buy"),
  SELL("Sell");

  private String value;

  Direction(String value) { // Constructor
    this.value = value;
  }

  public String getValue() { // Getter
    return value;
  }
}
