package com.inventory.service;

import com.inventory.config.CacheConfig;
import com.inventory.domain.Product;
import com.inventory.dto.PageResponse;
import com.inventory.dto.ProductRequest;
import com.inventory.dto.ProductResponse;
import com.inventory.exception.ConflictException;
import com.inventory.exception.NotFoundException;
import com.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;

    private final ProductRepository repository;

    @Transactional
    public ProductResponse create(Long tenantId, ProductRequest request) {
        String sku = request.sku().trim();
        if (repository.existsByTenantIdAndSkuIgnoreCase(tenantId, sku)) {
            throw new ConflictException("SKU '" + sku + "' already exists");
        }
        Product saved = repository.save(Product.builder()
                .tenantId(tenantId)
                .sku(sku)
                .name(request.name().trim())
                .description(request.description())
                .price(request.price())
                .lowStockThreshold(thresholdOrDefault(request))
                .build());
        return ProductResponse.from(saved);
    }

    /**
     * Cached in Redis. The key includes the tenant id so one tenant can never read another tenant's cached entry.
     * Evicted on update / deactivate.
     */
    @Cacheable(cacheNames = CacheConfig.PRODUCTS, key = "#tenantId + ':' + #id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(Long tenantId, String query, Pageable pageable) {
        String pattern = "%" + (query == null ? "" : query.trim().toLowerCase(Locale.ROOT)) + "%";
        return PageResponse.of(repository.search(tenantId, pattern, pageable), ProductResponse::from);
    }

    @CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#tenantId + ':' + #id")
    @Transactional
    public ProductResponse update(Long tenantId, Long id, ProductRequest request) {
        Product p = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        String sku = request.sku().trim();
        if (!p.getSku().equalsIgnoreCase(sku) && repository.existsByTenantIdAndSkuIgnoreCase(tenantId, sku)) {
            throw new ConflictException("SKU '" + sku + "' already exists");
        }
        p.setSku(sku);
        p.setName(request.name().trim());
        p.setDescription(request.description());
        p.setPrice(request.price());
        p.setLowStockThreshold(thresholdOrDefault(request));
        return ProductResponse.from(p);
    }

    /** Soft delete: order history and audit logs keep pointing at a valid product row. */
    @CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#tenantId + ':' + #id")
    @Transactional
    public void deactivate(Long tenantId, Long id) {
        Product p = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        p.setActive(false);
    }

    private int thresholdOrDefault(ProductRequest r) {
        return r.lowStockThreshold() == null ? DEFAULT_LOW_STOCK_THRESHOLD : r.lowStockThreshold();
    }
}
