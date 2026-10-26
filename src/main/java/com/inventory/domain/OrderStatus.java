package com.inventory.domain;

public enum OrderStatus {
    /** Accepted by the API, waiting for the async worker to reserve stock. */
    PENDING,
    /** Stock deducted successfully. */
    CONFIRMED,
    /** Could not be fulfilled (insufficient stock / no stock record). */
    REJECTED,
    CANCELLED
}
