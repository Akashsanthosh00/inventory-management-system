package com.inventory.service;

import com.inventory.domain.AuditReason;
import com.inventory.domain.StockAuditLog;
import com.inventory.domain.StockLevel;
import com.inventory.repository.StockAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes the audit row in the SAME transaction as the stock change, so the two can never diverge. */
@Service
@RequiredArgsConstructor
public class StockAuditService {

    private final StockAuditLogRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(StockLevel stock, int quantityBefore, AuditReason reason, String reference, String performedBy) {
        repository.save(StockAuditLog.builder()
                .tenantId(stock.getTenantId())
                .productId(stock.getProduct().getId())
                .productSku(stock.getProduct().getSku())
                .warehouseId(stock.getWarehouse().getId())
                .quantityBefore(quantityBefore)
                .delta(stock.getQuantity() - quantityBefore)
                .quantityAfter(stock.getQuantity())
                .reason(reason)
                .reference(reference)
                .performedBy(performedBy)
                .build());
    }
}
