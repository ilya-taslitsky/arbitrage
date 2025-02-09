package com.crypto.arbitrage.providers.mexc.config;

import com.crypto.arbitrage.providers.mexc.model.order.MexcLoginData;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Setter
@Getter
@Configuration
public class MexcConfig {
    @Value("${mexc.api.url}")
    private String apiUrl;
    private MexcLoginData loginData;

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}
