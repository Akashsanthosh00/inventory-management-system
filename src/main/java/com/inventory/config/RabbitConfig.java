package com.inventory.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology:
 * <pre>
 *  inventory.exchange (topic)
 *     order.placed  -> order.placed.queue  --(rejected after retries)--> inventory.dlx -> order.placed.dlq
 *     order.status  -> order.notification.queue
 * </pre>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "inventory.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "inventory.dlx";

    public static final String ORDER_PLACED_KEY = "order.placed";
    public static final String ORDER_STATUS_KEY = "order.status";

    public static final String ORDER_QUEUE = "order.placed.queue";
    public static final String ORDER_DLQ = "order.placed.dlq";
    public static final String NOTIFICATION_QUEUE = "order.notification.queue";

    @Bean
    TopicExchange inventoryExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue orderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ORDER_DLQ)
                .build();
    }

    @Bean
    Queue orderDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_DLQ).build();
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    Binding orderBinding(Queue orderQueue, TopicExchange inventoryExchange) {
        return BindingBuilder.bind(orderQueue).to(inventoryExchange).with(ORDER_PLACED_KEY);
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, TopicExchange inventoryExchange) {
        return BindingBuilder.bind(notificationQueue).to(inventoryExchange).with(ORDER_STATUS_KEY);
    }

    @Bean
    Binding deadLetterBinding(Queue orderDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(orderDeadLetterQueue).to(deadLetterExchange).with(ORDER_DLQ);
    }

    /** JSON on the wire instead of Java serialization. Spring Boot wires this into RabbitTemplate and the listeners. */
    @Bean
    MessageConverter jsonMessageConverter() {
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.inventory.*");
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
