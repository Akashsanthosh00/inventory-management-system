package com.inventory.dto;

import com.inventory.domain.AuditReason;
import com.inventory.domain.StockAuditLog;

import java.time.Instant;

public record AuditLogResponse(Long id, Long productId, String sku, Long warehouseId, int quantityBefore,
                               int delta, int quantityAfter, AuditReason reason, String reference,
                               String performedBy, Instant createdAt) {
    public static AuditLogResponse from(StockAuditLog a) {
        return new AuditLogResponse(a.getId(), a.getProductId(), a.getProductSku(), a.getWarehouseId(),
                a.getQuantityBefore(), a.getDelta(), a.getQuantityAfter(), a.getReason(), a.getReference(),
                a.getPerformedBy(), a.getCreatedAt());
    }
}
