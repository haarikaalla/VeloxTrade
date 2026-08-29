package com.veloxtrade.platform.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Dedicated HTTP clients for the C++ matching engine and the Python analytics service. */
@Configuration
public class HttpClientConfig {

    @Bean
    RestClient engineRestClient(PlatformProperties properties) {
        return build(properties.engine().baseUrl(), properties.engine().timeout());
    }

    @Bean
    RestClient analyticsRestClient(PlatformProperties properties) {
        return build(properties.analytics().baseUrl(), properties.analytics().timeout());
    }

    private static RestClient build(String baseUrl, Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Keeps any additional auto-configured clients from hanging on a slow dependency. */
    @Bean
    RestClientCustomizer defaultTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(2));
            factory.setReadTimeout(Duration.ofSeconds(5));
            builder.requestFactory(factory);
        };
    }
}
