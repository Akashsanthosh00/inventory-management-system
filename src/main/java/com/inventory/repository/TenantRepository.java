package com.inventory.repository;

import com.inventory.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    boolean existsByNameIgnoreCase(String name);
}
