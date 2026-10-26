package com.inventory.security;

import com.inventory.config.AppProperties;
import com.inventory.domain.AppUser;
import com.inventory.domain.Role;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static JwtService serviceWith(String secret, Duration ttl) {
        return new JwtService(new AppProperties(new AppProperties.Jwt(secret, ttl), null, null));
    }

    private static AppUser user() {
        AppUser u = AppUser.builder().tenantId(42L).email("owner@shop.test").fullName("Owner")
                .passwordHash("x").role(Role.ADMIN).build();
        u.setId(7L);
        return u;
    }

    @Test
    void roundTripsIdentityTenantAndRole() {
        JwtService jwt = serviceWith(randomSecret(), Duration.ofHours(1));

        var issued = jwt.issue(user());

        assertThat(jwt.parse(issued.token()))
                .contains(new AuthUser(7L, 42L, "owner@shop.test", Role.ADMIN));
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = serviceWith(randomSecret(), Duration.ofHours(1)).issue(user()).token();

        assertThat(serviceWith(randomSecret(), Duration.ofHours(1)).parse(token)).isEmpty();
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwt = serviceWith(randomSecret(), Duration.ofHours(1));
        String token = jwt.issue(user()).token();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(jwt.parse(tampered)).isEmpty();
    }

    @Test
    void rejectsExpiredAndGarbageTokens() {
        JwtService jwt = serviceWith(randomSecret(), Duration.ofSeconds(-30));

        assertThat(jwt.parse(jwt.issue(user()).token())).isEmpty();
        assertThat(jwt.parse("not-a-jwt")).isEmpty();
    }
}
