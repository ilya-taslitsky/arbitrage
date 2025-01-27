package com.crypto.arbitrage.service.datasource;

import com.crypto.arbitrage.data.entity.DatasourceInfo;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import velox.api.layer1.Layer1ApiDataListener;
import velox.api.layer1.Layer1ApiInstrumentListener;
import velox.api.layer1.Layer1ApiProvider;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.SubscribeInfo;

import java.util.Map;

@Component
@Scope("prototype")
public class DataSourceLocal {
    private Layer1ApiProvider provider;
    @Autowired
    @Getter
    private CEXEngine cexEngine;

    public void subscribe(DatasourceInfo dataSourceInfo, int subCount, Map<String, InstrumentInfo> aliasToInstrumentInfo) {
        cexEngine.initialize(dataSourceInfo, aliasToInstrumentInfo);
        provider.addListener((Layer1ApiInstrumentListener) cexEngine);
        provider.addListener((Layer1ApiDataListener) cexEngine);


        if (subCount == 0) {
                provider.subscribe(new SubscribeInfo(dataSourceInfo.getExchangePortAlias(), dataSourceInfo.getExchangePortExchange(), dataSourceInfo.getExchangePortType()));
        }
    }

    public void unSubscribe(String symbol, String exchange, String type, int subCount) {
        String instrument = symbol + exchange + type;
        cexEngine.disable();
        provider.removeListener((Layer1ApiDataListener) cexEngine);
        provider.removeListener((Layer1ApiInstrumentListener) cexEngine);

        if(subCount == 0) {
            throw new IllegalArgumentException("Provider is not subscribed for " + instrument);
        }
    }



    public void setProvider(Layer1ApiProvider provider) {
        this.provider = provider;
    }


}
