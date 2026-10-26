package com.inventory.dto;

import com.inventory.domain.LowStockAlert;

import java.time.Instant;

public record AlertResponse(Long id, Long productId, String sku, String productName, Long warehouseId,
                            String warehouseName, int quantity, int threshold, boolean resolved,
                            Instant createdAt, Instant resolvedAt) {
    public static AlertResponse from(LowStockAlert a) {
        return new AlertResponse(a.getId(), a.getProductId(), a.getProductSku(), a.getProductName(),
                a.getWarehouseId(), a.getWarehouseName(), a.getQuantity(), a.getThreshold(), a.isResolved(),
                a.getCreatedAt(), a.isResolved() ? a.getUpdatedAt() : null);
    }
}
