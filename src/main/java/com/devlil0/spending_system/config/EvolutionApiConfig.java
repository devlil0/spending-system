package com.devlil0.spending_system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EvolutionApiConfig {

    @Value("${evolution.base-url}")
    private String baseUrl;

    @Value("${evolution.api-key}")
    private String apiKey;

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("apikey", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

}
