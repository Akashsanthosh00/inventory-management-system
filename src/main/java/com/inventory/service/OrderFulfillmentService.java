package com.inventory.service;

import com.inventory.domain.AuditReason;
import com.inventory.domain.CustomerOrder;
import com.inventory.domain.OrderItem;
import com.inventory.domain.OrderStatus;
import com.inventory.domain.StockLevel;
import com.inventory.dto.OrderResponse;
import com.inventory.exception.BusinessException;
import com.inventory.exception.NotFoundException;
import com.inventory.messaging.OrderStatusMessage;
import com.inventory.repository.OrderRepository;
import com.inventory.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The transactional core of order handling: all-or-nothing stock deduction.
 * <p>
 * Overselling protection: every {@link StockLevel} row carries a {@code @Version}. When two workers deduct from the
 * same row concurrently, one commit fails with an optimistic-lock exception; {@link OrderProcessor} then re-runs the
 * whole transaction, which re-reads the new quantity and either succeeds or rejects the order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFulfillmentService {

    private final OrderRepository orderRepository;
    private final StockLevelRepository stockRepository;
    private final StockAuditService auditService;
    private final ApplicationEventPublisher events;

    @Transactional
    public void fulfil(Long orderId) {
        Optional<CustomerOrder> found = orderRepository.findWithItemsById(orderId);
        if (found.isEmpty()) {
            log.warn("Order {} not found, ignoring message", orderId);
            return;
        }
        CustomerOrder order = found.get();
        if (order.getStatus() != OrderStatus.PENDING) { // idempotent: redelivered / cancelled orders are skipped
            log.info("Order {} is already {}, skipping", order.getOrderNumber(), order.getStatus());
            return;
        }

        // 1) check everything first, 2) then deduct - the order is fulfilled completely or not at all
        List<StockLevel> stocks = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Optional<StockLevel> stock = stockRepository.findByTenantIdAndProductIdAndWarehouseId(
                    order.getTenantId(), item.getProductId(), order.getWarehouseId());
            int available = stock.map(StockLevel::getQuantity).orElse(0);
            if (available < item.getQuantity()) {
                reject(order, "Insufficient stock for " + item.getProductSku()
                        + " (requested " + item.getQuantity() + ", available " + available + ")");
                return;
            }
            stocks.add(stock.get());
        }

        for (int i = 0; i < stocks.size(); i++) {
            StockLevel stock = stocks.get(i);
            int before = stock.getQuantity();
            stock.setQuantity(before - order.getItems().get(i).getQuantity());
            auditService.record(stock, before, AuditReason.ORDER_FULFILLMENT, order.getOrderNumber(), order.getCreatedBy());
        }
        order.setStatus(OrderStatus.CONFIRMED);
        publishStatus(order, null);
        log.info("Order {} confirmed", order.getOrderNumber());
    }

    @Transactional
    public OrderResponse cancel(Long tenantId, String actor, Long orderId) {
        CustomerOrder order = orderRepository.findByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));

        switch (order.getStatus()) {
            case PENDING -> order.setStatus(OrderStatus.CANCELLED);
            case CONFIRMED -> {
                for (OrderItem item : order.getItems()) { // put the stock back
                    StockLevel stock = stockRepository.findByTenantIdAndProductIdAndWarehouseId(
                                    tenantId, item.getProductId(), order.getWarehouseId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Stock row vanished for product " + item.getProductId()));
                    int before = stock.getQuantity();
                    stock.setQuantity(before + item.getQuantity());
                    auditService.record(stock, before, AuditReason.ORDER_CANCELLATION, order.getOrderNumber(), actor);
                }
                order.setStatus(OrderStatus.CANCELLED);
            }
            default -> throw new BusinessException("Order is already " + order.getStatus());
        }
        publishStatus(order, "Cancelled by " + actor);
        return OrderResponse.from(order);
    }

    private void reject(CustomerOrder order, String reason) {
        order.setStatus(OrderStatus.REJECTED);
        order.setFailureReason(reason);
        publishStatus(order, reason);
        log.info("Order {} rejected: {}", order.getOrderNumber(), reason);
    }

    private void publishStatus(CustomerOrder order, String reason) {
        events.publishEvent(new OrderStatusMessage(order.getId(), order.getTenantId(), order.getOrderNumber(),
                order.getStatus(), order.getCustomerEmail(), reason)); // delivered AFTER_COMMIT
    }
}
