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
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "DEX_ID")
    private Dex dex;
    @ManyToOne
    @JoinColumn(name = "DATASOURCE_ID")
    private DatasourceInfo datasourceInfo;
}
