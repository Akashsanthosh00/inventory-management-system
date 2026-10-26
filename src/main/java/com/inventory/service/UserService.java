package com.inventory.service;

import com.inventory.domain.AppUser;
import com.inventory.dto.UserRequest;
import com.inventory.dto.UserResponse;
import com.inventory.exception.ConflictException;
import com.inventory.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(Long tenantId, UserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email already registered");
        }
        AppUser user = userRepository.save(AppUser.builder()
                .tenantId(tenantId)
                .email(request.email().trim().toLowerCase())
                .fullName(request.fullName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(Long tenantId) {
        return userRepository.findByTenantIdOrderByIdAsc(tenantId).stream().map(UserResponse::from).toList();
    }
}
