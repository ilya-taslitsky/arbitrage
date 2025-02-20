package com.crypto.arbitrage.providers.orc.model;

import java.util.List;
import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrcAllPoolsInfoResp {
  private OrcMeta meta;
  private List<OrcPoolData> data;
}
