package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.aspect.annotation.Authorized;
import com.crypto.arbitrage.data.RestResponse;
import com.crypto.arbitrage.data.dto.DatasourceInfoDto;
import com.crypto.arbitrage.mapper.DatasourceInfoMapper;
import com.crypto.arbitrage.service.DatasourceInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/datasources")
@RequiredArgsConstructor
public class DatasourceInfoController {
    private final DatasourceInfoService datasourceService;
    private final DatasourceInfoMapper datasourceInfoMapper;
    
    @Authorized
    @PostMapping
    public RestResponse createDatasource(@RequestBody @Valid DatasourceInfoDto dto) {
        return new RestResponse(datasourceInfoMapper.toDto(datasourceService.save(datasourceInfoMapper.toEntity(dto))));
    }

}
