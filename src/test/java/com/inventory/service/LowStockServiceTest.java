package com.inventory.service;

import com.inventory.domain.LowStockAlert;
import com.inventory.domain.Product;
import com.inventory.domain.StockLevel;
import com.inventory.domain.Warehouse;
import com.inventory.repository.LowStockAlertRepository;
import com.inventory.repository.StockLevelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LowStockServiceTest {

    @Mock StockLevelRepository stockRepository;
    @Mock LowStockAlertRepository alertRepository;
    @Mock NotificationService notificationService;
    @InjectMocks LowStockService service;

    private StockLevel lowStock;

    @BeforeEach
    void setUp() {
        Product p = Product.builder().tenantId(1L).sku("MILK").name("Milk 1L")
                .price(BigDecimal.TEN).lowStockThreshold(5).build();
        p.setId(10L);
        Warehouse w = Warehouse.builder().tenantId(1L).name("Main").build();
        w.setId(20L);
        lowStock = new StockLevel(1L, p, w, 3);
        lowStock.setId(30L);
    }

    @Test
    void createsAnAlertForNewlyLowStockAndNotifies() {
        when(stockRepository.findAllBelowThreshold()).thenReturn(List.of(lowStock));
        when(alertRepository.existsByStockLevelIdAndResolvedFalse(30L)).thenReturn(false);
        when(alertRepository.save(any(LowStockAlert.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertRepository.findByResolvedFalse()).thenReturn(List.of());

        int created = service.scan();

        assertThat(created).isEqualTo(1);
        verify(notificationService).sendLowStock(any(LowStockAlert.class));
    }

    @Test
    void doesNotRaiseDuplicateAlertWhileOneIsAlreadyOpen() {
        LowStockAlert open = LowStockAlert.builder().stockLevelId(30L).build();
        when(stockRepository.findAllBelowThreshold()).thenReturn(List.of(lowStock));
        when(alertRepository.existsByStockLevelIdAndResolvedFalse(30L)).thenReturn(true);
        when(alertRepository.findByResolvedFalse()).thenReturn(List.of(open));

        int created = service.scan();

        assertThat(created).isZero();
        assertThat(open.isResolved()).isFalse();
        verify(alertRepository, never()).save(any());
        verify(notificationService, never()).sendLowStock(any());
    }

    @Test
    void resolvesAlertsOnceStockIsReplenished() {
        LowStockAlert open = LowStockAlert.builder().stockLevelId(99L).build();
        when(stockRepository.findAllBelowThreshold()).thenReturn(List.of());
        when(alertRepository.findByResolvedFalse()).thenReturn(List.of(open));

        service.scan();

        assertThat(open.isResolved()).isTrue();
    }
}
