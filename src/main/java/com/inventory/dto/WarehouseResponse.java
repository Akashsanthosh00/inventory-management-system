package com.inventory.dto;

import com.inventory.domain.Warehouse;

public record WarehouseResponse(Long id, String name, String location) {
    public static WarehouseResponse from(Warehouse w) {
        return new WarehouseResponse(w.getId(), w.getName(), w.getLocation());
    }
}
