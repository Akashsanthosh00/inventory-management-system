package com.inventory.dto;

import com.inventory.domain.Role;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt,
                           Long userId, Long tenantId, String tenantName, String email, Role role) {
}
