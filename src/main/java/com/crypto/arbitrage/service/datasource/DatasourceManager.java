package com.crypto.arbitrage.service.datasource;

import com.crypto.arbitrage.dao.DatasourceInfoDao;
import com.crypto.arbitrage.data.entity.DatasourceInfo;
import com.crypto.arbitrage.exception.NotFoundException;
import com.crypto.arbitrage.service.CEXAccountService;
import com.crypto.arbitrage.service.messaging.PublishSubscribeService;
import com.bookmap.exchangeport.ExchangePort;
import com.bookmap.exchangeport.ProviderContext;
import com.bookmap.exchangeport.ProviderDef;
import com.bookmap.exchangeport.Providers;
import com.bookmap.exchangeport.credentials.BookmapDataCredentials;
import com.bookmap.exchangeport.credentials.CedroCredentials;
import com.bookmap.exchangeport.credentials.WithoutCredentials;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import velox.api.layer1.Layer1ApiProvider;
import velox.api.layer1.common.VersionHelper;
import velox.api.layer1.data.InstrumentInfo;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatasourceManager implements ApplicationContextAware {
    private final PublishSubscribeService publishSubscribeService;
    private final DatasourceInfoDao datasourceDao;
    private ApplicationContext applicationContext;
    private Map<String, ProviderContext<?>> providerContextCache = new HashMap<>();
    private final Map<String, DataSourceLocal> dataSourceLocalCache = new HashMap<>();
    private final Map<Layer1ApiProvider, Map<String, Integer>> providerToInstumentCountSubscribers = new HashMap<>();
    private final Map<String, Map<String, InstrumentInfo>> providerToAliasInstrumentInfo = new HashMap<>();
    private final CEXAccountService accountService;
    private final ExchangePort EXCHANGE_PORT = new ExchangePort("TOKEN-NOT-USED-RIGHT-NOW");


    @PostConstruct
    @Transactional
    public void initialize() {
        VersionHelper.setBookmapVersionOnce("exchangeport");
        List<DatasourceInfo> dataSourceInfos = datasourceDao.findAllByActive(true);

        dataSourceInfos.forEach(this::registerDatasource);
    }

    private void initCache(DatasourceInfo dataSourceInfo) {
        DataSourceLocal dataSource = applicationContext.getBean(DataSourceLocal.class);
        Layer1ApiProvider provider = getProvider(dataSourceInfo.getExchange());
        dataSource.setProvider(provider);
        dataSourceLocalCache.put(dataSourceInfo.getAlias(), dataSource);
    }

    public void registerDatasource(DatasourceInfo instrumentInfo) {
        initCache(instrumentInfo);
        processActivation(instrumentInfo, true);
    }

    private void processActivation(DatasourceInfo dataSourceInfo, boolean isRegister) {
        String symbol = dataSourceInfo.getSymbol();
        String exchange = null;
        String type = dataSourceInfo.getType();
        DataSourceLocal dataSource = dataSourceLocalCache.get(dataSourceInfo.getAlias());
        if(dataSource == null) {
            initCache(dataSourceInfo);
            dataSource = dataSourceLocalCache.get(dataSourceInfo.getAlias());
        }
        Map<String, Integer> instumentToSubCountMap = providerToInstumentCountSubscribers.get(getProvider(dataSourceInfo.getExchange()));
        String instrument = symbol + exchange + type;
        int subCount = instumentToSubCountMap.getOrDefault(instrument, 0);
        if(isRegister) {
            dataSource.subscribe(dataSourceInfo, subCount, providerToAliasInstrumentInfo.get(dataSourceInfo.getExchange()));
            instumentToSubCountMap.put(instrument, ++subCount);
        } else {
            dataSource.unSubscribe(dataSourceInfo.getExchangePortAlias(),
                    dataSourceInfo.getExchangePortExchange(), dataSourceInfo.getExchangePortType(), subCount);
            // due to we do not unsubsribe the provider we need it to avoid repeatable subscribtions
            // remove when we add provider.unsibscribe()
            if(subCount != 1) {
                instumentToSubCountMap.put(instrument, --subCount);
            }
        }
    }

    public void stopDatasource(Long id) {
        Optional<DatasourceInfo> optionalDataSourceInfo = datasourceDao.findById(id);
        DatasourceInfo dataSourceInfo = optionalDataSourceInfo.orElseThrow(() ->
                new NotFoundException("DataSource " + id + " does not exists"));
        activateDatasource(dataSourceInfo, false);
    }


    public void activateDatasource(DatasourceInfo dataSourceInfo, boolean activate) {
        if(dataSourceInfo.isActive() == activate) {
            log.error("Datasource is already in the state");
            return;
        }
        processActivation(dataSourceInfo, activate);
        dataSourceInfo.setActive(activate);
        datasourceDao.save(dataSourceInfo);
    }


    public void unregisterDatasource(DatasourceInfo instrumentInfo) {
        processActivation(instrumentInfo, false);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    public int getBidPrice(String alias) {
        DataSourceLocal dataSource = dataSourceLocalCache.get(alias);
        return dataSource.getCexEngine().getBidPrice();
    }

    public int getAskPrice(String alias) {
        DataSourceLocal dataSource = dataSourceLocalCache.get(alias);
        return dataSource.getCexEngine().getAskPrice();
    }


    private Layer1ApiProvider getProvider(String exchange) {
        if (providerContextCache.containsKey(exchange)) {
            return providerContextCache.get(exchange).provider();
        }

        ProviderContext<?> providerContext;
        switch (exchange) {
            case "BN" -> providerContext = getProvider(Providers.BINANCE);
            case "BNF" -> providerContext = getProvider(Providers.BINANCE_FUTURES);
            case "CNB" -> providerContext = getProvider(Providers.COINBASE_PRO);
            case "BTG" -> providerContext = getProvider(Providers.BITGET);
            default -> throw new RuntimeException("Provider is not supported: " + exchange);
        }

        providerToAliasInstrumentInfo.put(exchange, new HashMap<>());
        providerContextCache.put(exchange, providerContext);
        providerToInstumentCountSubscribers.put(providerContext.provider(), new HashMap<>());
        return providerContext.provider();
    }

    private ProviderContext<?> getProvider(ProviderDef<WithoutCredentials> providerDef) {
        return getProvider(providerDef, WithoutCredentials.value());
    }

    private <C> ProviderContext<C> getProvider(ProviderDef<C> providerDef, C credentials) {
        try  {
            ProviderContext<C> ctx = EXCHANGE_PORT.load(Path.of("libs"), providerDef);
            ctx.loginBlocking(credentials, Duration.ofMinutes(2));
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
