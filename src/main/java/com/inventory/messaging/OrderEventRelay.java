package com.inventory.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges Spring application events to the broker <b>only after the database transaction committed</b>.
 * Otherwise the worker could receive "order 42 placed" before order 42 is visible in the database
 * (or for a transaction that later rolled back).
 */
@Component
@RequiredArgsConstructor
public class OrderEventRelay {

    private final OrderMessagePublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedMessage message) {
        publisher.publish(message);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusMessage message) {
        publisher.publish(message);
    }
}
