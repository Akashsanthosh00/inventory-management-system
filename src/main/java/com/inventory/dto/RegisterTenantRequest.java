package com.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterTenantRequest(
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Size(max = 150) String adminName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
