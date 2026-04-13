package com.lantu.connect.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class GatewayHttpClientConfig {

    @Bean(name = "gatewayHttpClient")
    public HttpClient gatewayHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    @Bean(name = "gatewayHttpClientNoRedirect")
    public HttpClient gatewayHttpClientNoRedirect() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_2)
                .build();
    }
}
