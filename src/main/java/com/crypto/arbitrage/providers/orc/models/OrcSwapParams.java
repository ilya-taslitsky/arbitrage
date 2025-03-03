package com.crypto.arbitrage.providers.orc.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Parameters for a swap operation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrcSwapParams {
  private String poolAddress;
  private String poolTokenMintA;
  private String poolTokenMintB;
  private String userTokenAccountA;
  private String userTokenAccountB;
  private String inputMint; // Mint of the input token
  private boolean aToB; // Direction of swap
  private boolean exactIn; // Whether this is an exact input swap
}
