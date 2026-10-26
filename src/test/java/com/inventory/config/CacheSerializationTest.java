package com.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.inventory.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the Redis cache value format round-trips without needing a Redis server. */
class CacheSerializationTest {

    @Test
    void productResponseSurvivesRedisJsonRoundTrip() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        RedisCacheConfiguration cfg = CacheConfig.productCacheConfiguration(mapper);
        ProductResponse original = new ProductResponse(5L, "RICE-5KG", "Basmati Rice", "premium",
                new BigDecimal("499.00"), 10, true, Instant.parse("2026-01-01T10:15:30Z"), Instant.parse("2026-01-02T10:15:30Z"));

        ByteBuffer bytes = cfg.getValueSerializationPair().write(original);
        Object restored = cfg.getValueSerializationPair().read(bytes);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void cacheKeysAreNamespaced() {
        RedisCacheConfiguration cfg = CacheConfig.productCacheConfiguration(JsonMapper.builder().findAndAddModules().build());

        assertThat(cfg.getKeyPrefixFor(CacheConfig.PRODUCTS)).startsWith("inventory:products");
    }
}
