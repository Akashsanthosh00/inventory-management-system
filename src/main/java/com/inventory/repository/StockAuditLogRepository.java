package com.inventory.repository;

import com.inventory.domain.StockAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAuditLogRepository extends JpaRepository<StockAuditLog, Long> {
    Page<StockAuditLog> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);

    Page<StockAuditLog> findByTenantIdAndProductIdOrderByIdDesc(Long tenantId, Long productId, Pageable pageable);
}
