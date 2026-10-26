package com.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.dto.ProductResponse;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS = "products";

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheCustomizer(ObjectMapper objectMapper) {
        return builder -> builder.withCacheConfiguration(PRODUCTS, productCacheConfiguration(objectMapper));
    }

    /** Visible for tests. Values are stored as readable JSON, keys as plain strings ("inventory:products::{tenant}:{id}"). */
    public static RedisCacheConfiguration productCacheConfiguration(ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<ProductResponse> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper.copy(), ProductResponse.class);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .prefixCacheNameWith("inventory:")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }
}
