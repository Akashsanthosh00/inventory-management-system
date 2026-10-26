package com.inventory.messaging;

import com.inventory.domain.OrderStatus;

/** Published whenever an order changes status; consumed by the notification worker. */
public record OrderStatusMessage(Long orderId, Long tenantId, String orderNumber, OrderStatus status,
                                 String customerEmail, String reason) {
}
