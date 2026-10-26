package com.inventory.service;

import com.inventory.domain.CustomerOrder;
import com.inventory.domain.OrderItem;
import com.inventory.domain.OrderStatus;
import com.inventory.domain.Product;
import com.inventory.dto.OrderResponse;
import com.inventory.dto.PageResponse;
import com.inventory.dto.PlaceOrderRequest;
import com.inventory.exception.NotFoundException;
import com.inventory.messaging.OrderPlacedMessage;
import com.inventory.repository.OrderRepository;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.WarehouseRepository;
import com.inventory.util.OptimisticRetry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrderFulfillmentService fulfillmentService;
    private final ApplicationEventPublisher events;

    /**
     * Accepts the order (status PENDING) and returns immediately. Stock is reserved asynchronously by a
     * RabbitMQ consumer once this transaction has committed (see OrderEventRelay).
     */
    @Transactional
    public OrderResponse placeOrder(Long tenantId, String actor, PlaceOrderRequest request) {
        warehouseRepository.findByIdAndTenantId(request.warehouseId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Warehouse " + request.warehouseId() + " not found"));

        // merge duplicate lines of the same product
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        request.items().forEach(i -> quantities.merge(i.productId(), i.quantity(), Integer::sum));

        Map<Long, Product> products = new LinkedHashMap<>();
        productRepository.findByTenantIdAndIdInAndActiveTrue(tenantId, quantities.keySet())
                .forEach(p -> products.put(p.getId(), p));
        List<Long> missing = quantities.keySet().stream().filter(id -> !products.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new NotFoundException("Unknown or inactive product id(s): " + missing);
        }

        CustomerOrder order = new CustomerOrder();
        order.setTenantId(tenantId);
        order.setOrderNumber("ORD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        order.setStatus(OrderStatus.PENDING);
        order.setCustomerName(request.customerName().trim());
        order.setCustomerEmail(request.customerEmail());
        order.setWarehouseId(request.warehouseId());
        order.setCreatedBy(actor);

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : quantities.entrySet()) {
            Product p = products.get(e.getKey());
            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setProductSku(p.getSku());
            item.setProductName(p.getName());
            item.setQuantity(e.getValue());
            item.setUnitPrice(p.getPrice());
            order.addItem(item);
            total = total.add(item.lineTotal());
        }
        order.setTotalAmount(total);

        CustomerOrder saved = orderRepository.save(order);
        events.publishEvent(new OrderPlacedMessage(saved.getId(), tenantId)); // delivered AFTER_COMMIT
        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long tenantId, Long id) {
        return orderRepository.findByIdAndTenantId(id, tenantId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(Long tenantId, OrderStatus status, Pageable pageable) {
        var page = (status == null)
                ? orderRepository.findByTenantId(tenantId, pageable)
                : orderRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        return PageResponse.of(page, OrderResponse::from);
    }

    /** Not transactional on purpose: each retry attempt runs in its own transaction inside the other bean. */
    public OrderResponse cancel(Long tenantId, String actor, Long id) {
        return OptimisticRetry.run(() -> fulfillmentService.cancel(tenantId, actor, id));
    }
}
