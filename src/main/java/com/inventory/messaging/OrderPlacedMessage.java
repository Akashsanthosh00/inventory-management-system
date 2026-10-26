package com.inventory.messaging;

/** Published after the "place order" transaction commits; consumed by the async fulfilment worker. */
public record OrderPlacedMessage(Long orderId, Long tenantId) {
}
