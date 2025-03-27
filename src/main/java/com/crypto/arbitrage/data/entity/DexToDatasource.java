package com.crypto.arbitrage.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "dex_to_datasource")
public class DexToDatasource {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "dex_id")
  private Dex dex;

  @ManyToOne
  @JoinColumn(name = "datasource_id")
  private DatasourceInfo datasourceInfo;
}
