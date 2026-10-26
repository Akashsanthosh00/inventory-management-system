package com.inventory.repository;

import com.inventory.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByIdAndTenantId(Long id, Long tenantId);

    List<Warehouse> findByTenantIdOrderByNameAsc(Long tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);
}
