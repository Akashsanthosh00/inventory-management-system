package com.inventory.repository;

import com.inventory.domain.CustomerOrder;
import com.inventory.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByIdAndTenantId(Long id, Long tenantId);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findWithItemsById(Long id);

    Page<CustomerOrder> findByTenantId(Long tenantId, Pageable pageable);

    Page<CustomerOrder> findByTenantIdAndStatus(Long tenantId, OrderStatus status, Pageable pageable);
}
