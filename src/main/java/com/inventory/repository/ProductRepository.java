package com.inventory.repository;

import com.inventory.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Every query is scoped by tenantId - that is what isolates tenants from each other.
    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndSkuIgnoreCase(Long tenantId, String sku);

    List<Product> findByTenantIdAndIdInAndActiveTrue(Long tenantId, Collection<Long> ids);

    /** {@code pattern} must already be lower-cased and wrapped in % wildcards. */
    @Query("""
            select p from Product p
            where p.tenantId = :tenantId and p.active = true
              and (lower(p.name) like :pattern or lower(p.sku) like :pattern)
            """)
    Page<Product> search(@Param("tenantId") Long tenantId, @Param("pattern") String pattern, Pageable pageable);
}
