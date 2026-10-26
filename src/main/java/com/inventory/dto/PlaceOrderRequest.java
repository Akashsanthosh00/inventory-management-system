package com.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PlaceOrderRequest(
        @NotBlank @Size(max = 150) String customerName,
        @Email String customerEmail,
        @NotNull Long warehouseId,
        @NotEmpty @Valid List<Item> items) {

    public record Item(@NotNull Long productId, @Min(1) int quantity) {
    }
}
