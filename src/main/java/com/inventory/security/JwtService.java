package com.inventory.security;

import com.inventory.config.AppProperties;
import com.inventory.domain.AppUser;
import com.inventory.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    public record IssuedToken(String token, Instant expiresAt) {
    }

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.jwt().secret()));
        this.expiration = props.jwt().expiration();
    }

    /** The tenant id is baked into the signed token, so a client can never choose which tenant it acts on. */
    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant exp = now.plus(expiration);
        String token = Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("tid", user.getTenantId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, exp);
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(
                    ((Number) c.get("uid")).longValue(),
                    ((Number) c.get("tid")).longValue(),
                    c.getSubject(),
                    Role.valueOf(c.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
