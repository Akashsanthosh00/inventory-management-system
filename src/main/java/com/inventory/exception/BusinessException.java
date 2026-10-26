package com.inventory.exception;

/** A valid request that violates a business rule (e.g. stock would go negative). Mapped to HTTP 422. */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
