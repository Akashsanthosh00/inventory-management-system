package com.inventory.web;

import com.inventory.dto.WarehouseRequest;
import com.inventory.dto.WarehouseResponse;
import com.inventory.security.AuthUser;
import com.inventory.service.WarehouseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.create(user.tenantId(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public WarehouseResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(user.tenantId(), id, request);
    }

    @GetMapping
    public List<WarehouseResponse> list(@AuthenticationPrincipal AuthUser user) {
        return warehouseService.list(user.tenantId());
    }
}
