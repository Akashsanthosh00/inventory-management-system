package com.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, RateLimit rateLimit, LowStock lowStock) {

    public record Jwt(String secret, Duration expiration) {
    }

    public record RateLimit(boolean enabled, int capacity, int authCapacity, Duration refillPeriod) {
    }

    public record LowStock(String cron) {
    }
}
