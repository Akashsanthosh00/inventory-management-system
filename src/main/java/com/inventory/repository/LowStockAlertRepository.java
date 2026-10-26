package com.inventory.repository;

import com.inventory.domain.LowStockAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, Long> {

    boolean existsByStockLevelIdAndResolvedFalse(Long stockLevelId);

    List<LowStockAlert> findByResolvedFalse();

    Page<LowStockAlert> findByTenantIdAndResolvedOrderByIdDesc(Long tenantId, boolean resolved, Pageable pageable);
}
