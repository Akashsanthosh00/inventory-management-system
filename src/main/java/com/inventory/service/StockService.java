package com.inventory.service;

import com.inventory.domain.AuditReason;
import com.inventory.domain.Product;
import com.inventory.domain.StockLevel;
import com.inventory.domain.Warehouse;
import com.inventory.dto.AuditLogResponse;
import com.inventory.dto.PageResponse;
import com.inventory.dto.StockAdjustRequest;
import com.inventory.dto.StockResponse;
import com.inventory.exception.BusinessException;
import com.inventory.exception.NotFoundException;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.StockAuditLogRepository;
import com.inventory.repository.StockLevelRepository;
import com.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockLevelRepository stockRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockAuditLogRepository auditRepository;
    private final StockAuditService auditService;

    /**
     * Receive or correct stock. If another transaction changed the same row meanwhile, the @Version check fails
     * and the caller gets HTTP 409 (see GlobalExceptionHandler) instead of a lost update.
     */
    @Transactional
    public StockResponse adjust(Long tenantId, String actor, StockAdjustRequest request) {
        if (request.delta() == 0) {
            throw new BusinessException("delta must not be 0");
        }
        Product product = productRepository.findByIdAndTenantId(request.productId(), tenantId)
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Product " + request.productId() + " not found"));
        Warehouse warehouse = warehouseRepository.findByIdAndTenantId(request.warehouseId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Warehouse " + request.warehouseId() + " not found"));

        StockLevel stock = stockRepository
                .findByTenantIdAndProductIdAndWarehouseId(tenantId, product.getId(), warehouse.getId())
                .orElseGet(() -> new StockLevel(tenantId, product, warehouse, 0));

        int before = stock.getQuantity();
        int after = before + request.delta();
        if (after < 0) {
            throw new BusinessException("Stock cannot go negative (current " + before + ", delta " + request.delta() + ")");
        }
        stock.setQuantity(after);
        StockLevel saved = stockRepository.save(stock);

        AuditReason reason = request.delta() > 0 ? AuditReason.RESTOCK : AuditReason.MANUAL_ADJUSTMENT;
        auditService.record(saved, before, reason, request.note(), actor);
        return StockResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockResponse> list(Long tenantId, Long productId, Long warehouseId, Pageable pageable) {
        var page = (productId != null)
                ? stockRepository.findByTenantIdAndProductId(tenantId, productId, pageable)
                : (warehouseId != null)
                ? stockRepository.findByTenantIdAndWarehouseId(tenantId, warehouseId, pageable)
                : stockRepository.findByTenantId(tenantId, pageable);
        return PageResponse.of(page, StockResponse::from);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> lowStock(Long tenantId) {
        return stockRepository.findLowByTenant(tenantId).stream().map(StockResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> auditLog(Long tenantId, Long productId, Pageable pageable) {
        var page = (productId != null)
                ? auditRepository.findByTenantIdAndProductIdOrderByIdDesc(tenantId, productId, pageable)
                : auditRepository.findByTenantIdOrderByIdDesc(tenantId, pageable);
        return PageResponse.of(page, AuditLogResponse::from);
    }
}
