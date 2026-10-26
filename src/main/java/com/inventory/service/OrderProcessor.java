package com.inventory.service;

import com.inventory.util.OptimisticRetry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Entry point used by the RabbitMQ consumer. Retries when a concurrent order won the optimistic-lock race. */
@Service
@RequiredArgsConstructor
public class OrderProcessor {

    private final OrderFulfillmentService fulfillmentService;

    public void process(Long orderId) {
        OptimisticRetry.run(() -> {
            fulfillmentService.fulfil(orderId);
            return null;
        });
    }
}
