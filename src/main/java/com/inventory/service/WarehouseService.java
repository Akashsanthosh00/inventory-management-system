package com.inventory.service;

import com.inventory.domain.Warehouse;
import com.inventory.dto.WarehouseRequest;
import com.inventory.dto.WarehouseResponse;
import com.inventory.exception.ConflictException;
import com.inventory.exception.NotFoundException;
import com.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository repository;

    @Transactional
    public WarehouseResponse create(Long tenantId, WarehouseRequest request) {
        if (repository.existsByTenantIdAndNameIgnoreCase(tenantId, request.name().trim())) {
            throw new ConflictException("Warehouse '" + request.name() + "' already exists");
        }
        Warehouse saved = repository.save(Warehouse.builder()
                .tenantId(tenantId).name(request.name().trim()).location(request.location()).build());
        return WarehouseResponse.from(saved);
    }

    @Transactional
    public WarehouseResponse update(Long tenantId, Long id, WarehouseRequest request) {
        Warehouse w = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("Warehouse " + id + " not found"));
        if (!w.getName().equalsIgnoreCase(request.name().trim())
                && repository.existsByTenantIdAndNameIgnoreCase(tenantId, request.name().trim())) {
            throw new ConflictException("Warehouse '" + request.name() + "' already exists");
        }
        w.setName(request.name().trim());
        w.setLocation(request.location());
        return WarehouseResponse.from(w);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> list(Long tenantId) {
        return repository.findByTenantIdOrderByNameAsc(tenantId).stream().map(WarehouseResponse::from).toList();
    }
}
