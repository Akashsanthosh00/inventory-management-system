package com.inventory.service;

import com.inventory.domain.AppUser;
import com.inventory.domain.Role;
import com.inventory.domain.Tenant;
import com.inventory.dto.AuthResponse;
import com.inventory.dto.LoginRequest;
import com.inventory.dto.RegisterTenantRequest;
import com.inventory.exception.ConflictException;
import com.inventory.repository.TenantRepository;
import com.inventory.repository.UserRepository;
import com.inventory.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Onboards a new business: creates the tenant and its first ADMIN user. */
    @Transactional
    public AuthResponse registerTenant(RegisterTenantRequest request) {
        if (tenantRepository.existsByNameIgnoreCase(request.businessName().trim())) {
            throw new ConflictException("A business named '" + request.businessName() + "' already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email already registered");
        }
        Tenant tenant = tenantRepository.save(Tenant.builder().name(request.businessName().trim()).build());
        AppUser admin = userRepository.save(AppUser.builder()
                .tenantId(tenant.getId())
                .email(request.email().trim().toLowerCase())
                .fullName(request.adminName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .build());
        return toResponse(admin, tenant.getName());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(AppUser::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        String tenantName = tenantRepository.findById(user.getTenantId()).map(Tenant::getName).orElse("");
        return toResponse(user, tenantName);
    }

    private AuthResponse toResponse(AppUser user, String tenantName) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.token(), "Bearer", token.expiresAt(), user.getId(), user.getTenantId(),
                tenantName, user.getEmail(), user.getRole());
    }
}
