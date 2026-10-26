package com.inventory.service;

import com.inventory.domain.LowStockAlert;
import com.inventory.domain.StockLevel;
import com.inventory.dto.AlertResponse;
import com.inventory.dto.PageResponse;
import com.inventory.repository.LowStockAlertRepository;
import com.inventory.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LowStockService {

    private final StockLevelRepository stockRepository;
    private final LowStockAlertRepository alertRepository;
    private final NotificationService notificationService;

    /**
     * Raises one alert per (product, warehouse) that is at/below its threshold - but only once while it stays low -
     * and auto-resolves alerts whose stock has been replenished.
     *
     * @return number of NEW alerts created
     */
    @Transactional
    public int scan() {
        List<StockLevel> low = stockRepository.findAllBelowThreshold();
        Set<Long> lowIds = new HashSet<>();
        int created = 0;
        for (StockLevel s : low) {
            lowIds.add(s.getId());
            if (alertRepository.existsByStockLevelIdAndResolvedFalse(s.getId())) {
                continue;
            }
            LowStockAlert alert = alertRepository.save(LowStockAlert.builder()
                    .tenantId(s.getTenantId())
                    .stockLevelId(s.getId())
                    .productId(s.getProduct().getId())
                    .productSku(s.getProduct().getSku())
                    .productName(s.getProduct().getName())
                    .warehouseId(s.getWarehouse().getId())
                    .warehouseName(s.getWarehouse().getName())
                    .quantity(s.getQuantity())
                    .threshold(s.getProduct().getLowStockThreshold())
                    .build());
            notificationService.sendLowStock(alert);
            created++;
        }
        for (LowStockAlert open : alertRepository.findByResolvedFalse()) {
            if (!lowIds.contains(open.getStockLevelId())) {
                open.setResolved(true);
            }
        }
        return created;
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> list(Long tenantId, boolean resolved, Pageable pageable) {
        return PageResponse.of(
                alertRepository.findByTenantIdAndResolvedOrderByIdDesc(tenantId, resolved, pageable),
                AlertResponse::from);
    }
}
