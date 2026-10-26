package com.inventory.service;

import com.inventory.domain.AuditReason;
import com.inventory.domain.CustomerOrder;
import com.inventory.domain.OrderItem;
import com.inventory.domain.OrderStatus;
import com.inventory.domain.Product;
import com.inventory.domain.StockLevel;
import com.inventory.domain.Warehouse;
import com.inventory.exception.BusinessException;
import com.inventory.messaging.OrderStatusMessage;
import com.inventory.repository.OrderRepository;
import com.inventory.repository.StockLevelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFulfillmentServiceTest {

    private static final long TENANT = 1L;
    private static final long WAREHOUSE = 7L;

    @Mock OrderRepository orderRepository;
    @Mock StockLevelRepository stockRepository;
    @Mock StockAuditService auditService;
    @Mock ApplicationEventPublisher events;
    @InjectMocks OrderFulfillmentService service;

    private Product shirt;
    private Product mug;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        shirt = product(10L, "SHIRT");
        mug = product(11L, "MUG");
        warehouse = new Warehouse();
        warehouse.setId(WAREHOUSE);
        warehouse.setName("Main");
    }

    @Test
    void confirmsOrderDeductsStockAndWritesAuditTrail() {
        CustomerOrder order = order(OrderStatus.PENDING, item(shirt, 3));
        StockLevel stock = new StockLevel(TENANT, shirt, warehouse, 10);
        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
        when(stockRepository.findByTenantIdAndProductIdAndWarehouseId(TENANT, 10L, WAREHOUSE))
                .thenReturn(Optional.of(stock));

        service.fulfil(100L);

        assertThat(stock.getQuantity()).isEqualTo(7);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(auditService).record(stock, 10, AuditReason.ORDER_FULFILLMENT, "ORD-1", "staff@shop.test");
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(((OrderStatusMessage) event.getValue()).status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void rejectsWholeOrderAndLeavesAllStockUntouchedWhenOneLineIsShort() {
        CustomerOrder order = order(OrderStatus.PENDING, item(shirt, 2), item(mug, 5));
        StockLevel shirtStock = new StockLevel(TENANT, shirt, warehouse, 10);
        StockLevel mugStock = new StockLevel(TENANT, mug, warehouse, 4); // short by one
        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
        when(stockRepository.findByTenantIdAndProductIdAndWarehouseId(TENANT, 10L, WAREHOUSE))
                .thenReturn(Optional.of(shirtStock));
        when(stockRepository.findByTenantIdAndProductIdAndWarehouseId(TENANT, 11L, WAREHOUSE))
                .thenReturn(Optional.of(mugStock));

        service.fulfil(100L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getFailureReason()).contains("MUG").contains("requested 5").contains("available 4");
        assertThat(shirtStock.getQuantity()).isEqualTo(10);
        assertThat(mugStock.getQuantity()).isEqualTo(4);
        verifyNoInteractions(auditService);
    }

    @Test
    void rejectsWhenProductHasNoStockRecordInWarehouse() {
        CustomerOrder order = order(OrderStatus.PENDING, item(shirt, 1));
        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));
        when(stockRepository.findByTenantIdAndProductIdAndWarehouseId(anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());

        service.fulfil(100L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getFailureReason()).contains("available 0");
    }

    @Test
    void ignoresOrdersThatAreNoLongerPending() {
        CustomerOrder order = order(OrderStatus.CANCELLED, item(shirt, 1));
        when(orderRepository.findWithItemsById(100L)).thenReturn(Optional.of(order));

        service.fulfil(100L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(stockRepository, auditService, events);
    }

    @Test
    void cancellingConfirmedOrderReturnsStock() {
        CustomerOrder order = order(OrderStatus.CONFIRMED, item(shirt, 3));
        StockLevel stock = new StockLevel(TENANT, shirt, warehouse, 7);
        when(orderRepository.findByIdAndTenantId(100L, TENANT)).thenReturn(Optional.of(order));
        when(stockRepository.findByTenantIdAndProductIdAndWarehouseId(TENANT, 10L, WAREHOUSE))
                .thenReturn(Optional.of(stock));

        var response = service.cancel(TENANT, "admin@shop.test", 100L);

        assertThat(stock.getQuantity()).isEqualTo(10);
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(auditService).record(eq(stock), eq(7), eq(AuditReason.ORDER_CANCELLATION), eq("ORD-1"), eq("admin@shop.test"));
    }

    @Test
    void cancellingPendingOrderDoesNotTouchStock() {
        CustomerOrder order = order(OrderStatus.PENDING, item(shirt, 3));
        when(orderRepository.findByIdAndTenantId(100L, TENANT)).thenReturn(Optional.of(order));

        service.cancel(TENANT, "admin@shop.test", 100L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(stockRepository, auditService);
    }

    @Test
    void rejectedOrdersCannotBeCancelled() {
        CustomerOrder order = order(OrderStatus.REJECTED, item(shirt, 3));
        when(orderRepository.findByIdAndTenantId(100L, TENANT)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(TENANT, "admin@shop.test", 100L))
                .isInstanceOf(BusinessException.class);
    }

    // ---- helpers ----

    private static Product product(long id, String sku) {
        Product p = Product.builder().tenantId(TENANT).sku(sku).name(sku.toLowerCase())
                .price(new BigDecimal("100.00")).lowStockThreshold(5).build();
        p.setId(id);
        return p;
    }

    private static OrderItem item(Product p, int qty) {
        OrderItem i = new OrderItem();
        i.setProductId(p.getId());
        i.setProductSku(p.getSku());
        i.setProductName(p.getName());
        i.setQuantity(qty);
        i.setUnitPrice(p.getPrice());
        return i;
    }

    private static CustomerOrder order(OrderStatus status, OrderItem... items) {
        CustomerOrder o = new CustomerOrder();
        o.setId(100L);
        o.setTenantId(TENANT);
        o.setOrderNumber("ORD-1");
        o.setStatus(status);
        o.setCustomerName("Customer");
        o.setWarehouseId(WAREHOUSE);
        o.setCreatedBy("staff@shop.test");
        for (OrderItem i : items) {
            o.addItem(i);
        }
        return o;
    }
}
