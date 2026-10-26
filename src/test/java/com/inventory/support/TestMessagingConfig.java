package com.inventory.support;

import com.inventory.messaging.OrderMessagePublisher;
import com.inventory.messaging.OrderPlacedMessage;
import com.inventory.messaging.OrderStatusMessage;
import com.inventory.service.OrderProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Replaces RabbitMQ in the fast H2 test suite: "published" orders are processed on a small thread pool,
 * which behaves like several concurrent consumers (and still runs AFTER the publishing transaction committed).
 */
@TestConfiguration
public class TestMessagingConfig {

    @Bean
    @Primary
    OrderMessagePublisher inMemoryOrderMessagePublisher(OrderProcessor processor) {
        return new InMemoryPublisher(processor);
    }

    @Slf4j
    static class InMemoryPublisher implements OrderMessagePublisher, DisposableBean {
        private final OrderProcessor processor;
        private final ExecutorService workers = Executors.newFixedThreadPool(6);

        InMemoryPublisher(OrderProcessor processor) {
            this.processor = processor;
        }

        @Override
        public void publish(OrderPlacedMessage message) {
            workers.submit(() -> {
                try {
                    processor.process(message.orderId());
                } catch (Exception e) {
                    log.error("Order processing failed for {}", message.orderId(), e);
                }
            });
        }

        @Override
        public void publish(OrderStatusMessage message) {
            // notifications are irrelevant for these tests
        }

        @Override
        public void destroy() {
            workers.shutdownNow();
        }
    }
}
