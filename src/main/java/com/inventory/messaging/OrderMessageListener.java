package com.inventory.messaging;

import com.inventory.config.RabbitConfig;
import com.inventory.service.NotificationService;
import com.inventory.service.OrderProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumers. If a handler throws, Spring retries (see spring.rabbitmq.listener.simple.retry.*) and then
 * rejects the message without requeue, which dead-letters it into order.placed.dlq for inspection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageListener {

    private final OrderProcessor orderProcessor;
    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitConfig.ORDER_QUEUE)
    public void onOrderPlaced(OrderPlacedMessage message) {
        log.info("Processing order {} (tenant {})", message.orderId(), message.tenantId());
        orderProcessor.process(message.orderId());
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void onOrderStatus(OrderStatusMessage message) {
        notificationService.sendOrderStatus(message);
    }
}
