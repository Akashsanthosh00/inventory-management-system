package com.inventory.messaging;

import com.inventory.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitOrderMessagePublisher implements OrderMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(OrderPlacedMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ORDER_PLACED_KEY, message);
        log.debug("Published order.placed for order {}", message.orderId());
    }

    @Override
    public void publish(OrderStatusMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ORDER_STATUS_KEY, message);
        log.debug("Published order.status {} for order {}", message.status(), message.orderId());
    }
}
