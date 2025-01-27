package com.crypto.arbitrage.dao;

import com.crypto.arbitrage.data.entity.DatasourceInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasourceInfoDao extends JpaRepository<DatasourceInfo, Long> {
    List<DatasourceInfo> findAllByActive(boolean active);
}
