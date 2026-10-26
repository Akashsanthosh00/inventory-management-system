package com.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Positive delta = receive stock, negative delta = write off / correct downwards. */
public record StockAdjustRequest(
        @NotNull Long productId,
        @NotNull Long warehouseId,
        @NotNull Integer delta,
        @Size(max = 255) String note) {
}
