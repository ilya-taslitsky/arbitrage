package com.crypto.arbitrage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ArbitrageConfig {
    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}
