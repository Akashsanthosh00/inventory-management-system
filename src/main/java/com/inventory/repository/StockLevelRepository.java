package com.inventory.repository;

import com.inventory.domain.StockLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    @EntityGraph(attributePaths = {"product", "warehouse"})
    Optional<StockLevel> findByTenantIdAndProductIdAndWarehouseId(Long tenantId, Long productId, Long warehouseId);

    @EntityGraph(attributePaths = {"product", "warehouse"})
    Page<StockLevel> findByTenantId(Long tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "warehouse"})
    Page<StockLevel> findByTenantIdAndProductId(Long tenantId, Long productId, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "warehouse"})
    Page<StockLevel> findByTenantIdAndWarehouseId(Long tenantId, Long warehouseId, Pageable pageable);

    /** Stock rows (all tenants) at or below their product's threshold. Used by the scheduled scan. */
    @Query("""
            select s from StockLevel s
              join fetch s.product p
              join fetch s.warehouse w
            where p.active = true and s.quantity <= p.lowStockThreshold
            """)
    List<StockLevel> findAllBelowThreshold();

    @Query("""
            select s from StockLevel s
              join fetch s.product p
              join fetch s.warehouse w
            where s.tenantId = :tenantId and p.active = true and s.quantity <= p.lowStockThreshold
            """)
    List<StockLevel> findLowByTenant(@org.springframework.data.repository.query.Param("tenantId") Long tenantId);
}
