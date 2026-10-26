package com.inventory.messaging;

/** Port for sending order events to the broker (lets tests swap the transport out). */
public interface OrderMessagePublisher {

    void publish(OrderPlacedMessage message);

    void publish(OrderStatusMessage message);
}
