package com.inventory.dto;

import com.inventory.domain.CustomerOrder;
import com.inventory.domain.OrderItem;
import com.inventory.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, String orderNumber, OrderStatus status, String customerName,
                            String customerEmail, Long warehouseId, BigDecimal totalAmount,
                            String failureReason, String createdBy, Instant createdAt, Instant updatedAt,
                            List<Item> items) {

    public record Item(Long productId, String sku, String productName, int quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal) {
        static Item from(OrderItem i) {
            return new Item(i.getProductId(), i.getProductSku(), i.getProductName(), i.getQuantity(),
                    i.getUnitPrice(), i.lineTotal());
        }
    }

    public static OrderResponse from(CustomerOrder o) {
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getStatus(), o.getCustomerName(),
                o.getCustomerEmail(), o.getWarehouseId(), o.getTotalAmount(), o.getFailureReason(),
                o.getCreatedBy(), o.getCreatedAt(), o.getUpdatedAt(),
                o.getItems().stream().map(Item::from).toList());
    }
}
