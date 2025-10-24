package com.example.CUSTOMERDATASEARCH;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;

@Configuration
public class RestTemplateConfig {

    @Value("${laserfiche.api.timeout.connect:10000}")
    private int connectTimeout;

    @Value("${laserfiche.api.timeout.read:60000}")
    private int readTimeout;

    @Value("${http.client.max.connections:50}")
    private int maxConnections;

    @Value("${http.client.max.connections.per.route:10}")
    private int maxConnectionsPerRoute;

    @Bean
    public RestTemplate restTemplate() {
        // Configure connection pooling
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnections);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);

        // Build HTTP client with timeouts
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        // Create request factory with timeouts
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(connectTimeout);
        factory.setConnectionRequestTimeout(readTimeout);

        return new RestTemplate(factory);
    }
}