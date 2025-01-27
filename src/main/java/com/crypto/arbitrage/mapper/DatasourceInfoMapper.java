package com.crypto.arbitrage.mapper;

import com.crypto.arbitrage.data.dto.DatasourceInfoDto;
import com.crypto.arbitrage.data.entity.DatasourceInfo;
import com.crypto.arbitrage.service.DatasourceInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatasourceInfoMapper {
    private final DatasourceInfoService datasourceInfoService;

    public DatasourceInfo toEntity(DatasourceInfoDto datasourceInfoDto) {
        DatasourceInfo datasourceInfo = new DatasourceInfo();
        datasourceInfo.setActive(datasourceInfoDto.isActive());
        datasourceInfo.setAlias(datasourceInfoDto.getAlias());
        datasourceInfo.setCommissionFee(datasourceInfoDto.getCommissionFee());
        datasourceInfo.setExchange(datasourceInfoDto.getExchange());
        datasourceInfo.setPipsSize(datasourceInfoDto.getPipsSize());
        datasourceInfo.setPriceMultiplier(datasourceInfoDto.getPriceMultiplier());
        datasourceInfo.setSizeMultiplier(datasourceInfoDto.getSizeMultiplier());
        datasourceInfo.setSymbol(datasourceInfoDto.getSymbol());
        datasourceInfo.setType(datasourceInfoDto.getType());
        datasourceInfo.setExchangePortAlias(datasourceInfoDto.getExchangePortAlias());
        datasourceInfo.setExchangePortExchange(datasourceInfoDto.getExchangePortExchange());
        datasourceInfo.setExchangePortType(datasourceInfoDto.getExchangePortType());


        return datasourceInfo;
    }
    public DatasourceInfoDto toDto(DatasourceInfo datasourceInfo) {
        DatasourceInfoDto result = new DatasourceInfoDto();
        result.setAlias(datasourceInfo.getAlias());
        result.setType(datasourceInfo.getType());
        result.setExchange(datasourceInfo.getExchange());
        result.setSymbol(datasourceInfo.getSymbol());
        result.setId(datasourceInfo.getId());
        result.setCommissionFee(datasourceInfo.getCommissionFee());
        result.setActive(datasourceInfo.isActive());
        result.setPipsSize(datasourceInfo.getPipsSize());
        result.setSizeMultiplier(datasourceInfo.getSizeMultiplier());
        result.setPriceMultiplier(datasourceInfo.getPriceMultiplier());
        result.setExchangePortAlias(datasourceInfo.getExchangePortAlias());
        result.setExchangePortExchange(datasourceInfo.getExchangePortExchange());
        result.setExchangePortType(datasourceInfo.getExchangePortType());
        return result;
    }
}
