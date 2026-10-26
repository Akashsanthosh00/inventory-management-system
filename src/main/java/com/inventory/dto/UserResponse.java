package com.inventory.dto;

import com.inventory.domain.AppUser;
import com.inventory.domain.Role;

public record UserResponse(Long id, String fullName, String email, Role role, boolean active) {
    public static UserResponse from(AppUser u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isActive());
    }
}
