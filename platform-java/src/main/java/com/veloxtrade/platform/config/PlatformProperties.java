package com.veloxtrade.platform.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed configuration for the platform. Every value is overridable
 * through environment variables so the same image runs locally and in Kubernetes.
 */
@ConfigurationProperties(prefix = "veloxtrade")
public record PlatformProperties(
        String symbol,
        BigDecimal openingCash,
        Duration tickInterval,
        List<String> allowedOrigins,
        Engine engine,
        Analytics analytics,
        Security security) {

    public record Engine(String baseUrl, Duration timeout) {
    }

    public record Analytics(String baseUrl, Duration timeout) {
    }

    public record Security(String jwtSecret, Duration tokenTtl) {
    }
}
