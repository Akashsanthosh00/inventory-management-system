package com.inventory.dto;

import com.inventory.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;

/** Also the value stored in the Redis product cache (serialized as JSON). */
public record ProductResponse(Long id, String sku, String name, String description, BigDecimal price,
                              int lowStockThreshold, boolean active, Instant createdAt, Instant updatedAt) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getPrice(),
                p.getLowStockThreshold(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
