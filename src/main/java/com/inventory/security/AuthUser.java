package com.inventory.security;

import com.inventory.domain.Role;

/** Authenticated principal rebuilt from the JWT on every request (stateless). */
public record AuthUser(Long userId, Long tenantId, String email, Role role) {
}
