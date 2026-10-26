package com.inventory.web;

import com.inventory.dto.AuditLogResponse;
import com.inventory.dto.PageResponse;
import com.inventory.dto.StockAdjustRequest;
import com.inventory.dto.StockResponse;
import com.inventory.security.AuthUser;
import com.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@Tag(name = "Stock")
public class StockController {

    private final StockService stockService;

    @Operation(summary = "Receive (positive delta) or write off (negative delta) stock - ADMIN")
    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public StockResponse adjust(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody StockAdjustRequest request) {
        return stockService.adjust(user.tenantId(), user.email(), request);
    }

    @Operation(summary = "Stock levels, optionally filtered by product or warehouse")
    @GetMapping
    public PageResponse<StockResponse> list(@AuthenticationPrincipal AuthUser user,
                                            @RequestParam(required = false) Long productId,
                                            @RequestParam(required = false) Long warehouseId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return stockService.list(user.tenantId(), productId, warehouseId, pageable(page, size));
    }

    @Operation(summary = "Stock rows currently at or below their low-stock threshold")
    @GetMapping("/low")
    public List<StockResponse> lowStock(@AuthenticationPrincipal AuthUser user) {
        return stockService.lowStock(user.tenantId());
    }

    @Operation(summary = "Audit trail of every stock movement (newest first)")
    @GetMapping("/audit")
    public PageResponse<AuditLogResponse> audit(@AuthenticationPrincipal AuthUser user,
                                                @RequestParam(required = false) Long productId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return stockService.auditLog(user.tenantId(), productId, pageable(page, size));
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }
}
