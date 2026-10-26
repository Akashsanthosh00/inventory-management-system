package com.inventory.dto;

import com.inventory.domain.StockLevel;

public record StockResponse(Long stockId, Long productId, String sku, String productName,
                            Long warehouseId, String warehouseName, int quantity,
                            int lowStockThreshold, boolean lowStock) {
    public static StockResponse from(StockLevel s) {
        return new StockResponse(s.getId(), s.getProduct().getId(), s.getProduct().getSku(),
                s.getProduct().getName(), s.getWarehouse().getId(), s.getWarehouse().getName(),
                s.getQuantity(), s.getProduct().getLowStockThreshold(), s.isLow());
    }
}
