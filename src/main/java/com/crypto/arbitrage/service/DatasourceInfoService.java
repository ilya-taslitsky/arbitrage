package com.crypto.arbitrage.service;

import com.crypto.arbitrage.data.entity.DatasourceInfo;

public interface DatasourceInfoService {
    DatasourceInfo save(DatasourceInfo datasourceInfo);
    DatasourceInfo findById(Long id);
}
