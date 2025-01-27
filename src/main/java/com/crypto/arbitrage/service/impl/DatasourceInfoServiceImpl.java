package com.crypto.arbitrage.service.impl;


import com.crypto.arbitrage.dao.DatasourceInfoDao;
import com.crypto.arbitrage.data.entity.DatasourceInfo;
import com.crypto.arbitrage.exception.NotFoundException;
import com.crypto.arbitrage.service.DatasourceInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
@RequiredArgsConstructor
public class DatasourceInfoServiceImpl implements DatasourceInfoService {
    private final DatasourceInfoDao datasourceInfoDao;

    @Override
    @Transactional
    public DatasourceInfo save(DatasourceInfo datasourceInfo) {
       return datasourceInfoDao.save(datasourceInfo);
    }

    @Override
    public DatasourceInfo findById(Long id) {
         return datasourceInfoDao.findById(id).orElseThrow(() -> new NotFoundException("Datasource with id " + id + " not found"));
    }
}
