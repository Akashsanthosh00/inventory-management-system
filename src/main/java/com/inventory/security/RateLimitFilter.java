package com.inventory.security;

import com.inventory.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token-bucket rate limiter (Bucket4j).
 * <ul>
 *   <li>Authenticated calls are limited per user.</li>
 *   <li>Anonymous calls and everything under /api/auth/** are limited per client IP (stricter for auth).</li>
 * </ul>
 * Buckets live in memory, which is fine for a single instance. For several instances you would back
 * Bucket4j with Redis (bucket4j-redis) so the limit is shared.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long IDLE_EVICT_NANOS = TimeUnit.MINUTES.toNanos(10);
    private static final int CLEANUP_EVERY = 1_000;

    private static final class Entry {
        final Bucket bucket;
        volatile long lastAccess = System.nanoTime();

        Entry(Bucket bucket) {
            this.bucket = bucket;
        }
    }

    private final AppProperties.RateLimit config;
    private final ConcurrentMap<String, Entry> buckets = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong();

    public RateLimitFilter(AppProperties.RateLimit config) {
        this.config = config;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean authEndpoint = request.getRequestURI().startsWith("/api/auth/");
        int capacity = authEndpoint ? config.authCapacity() : config.capacity();

        Entry entry = buckets.computeIfAbsent(resolveKey(request, authEndpoint), k -> new Entry(newBucket(capacity)));
        entry.lastAccess = System.nanoTime();
        evictIdleOccasionally();

        ConsumptionProbe probe = entry.bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":"
                + "\"Rate limit exceeded. Retry in " + retryAfter + "s.\"}");
    }

    private Bucket newBucket(int capacity) {
        Duration period = config.refillPeriod();
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                .build();
    }

    private String resolveKey(HttpServletRequest request, boolean authEndpoint) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!authEndpoint && auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return "user:" + user.userId();
        }
        return (authEndpoint ? "auth-ip:" : "ip:") + request.getRemoteAddr();
    }

    private void evictIdleOccasionally() {
        if (counter.incrementAndGet() % CLEANUP_EVERY == 0) {
            long now = System.nanoTime();
            buckets.entrySet().removeIf(e -> now - e.getValue().lastAccess > IDLE_EVICT_NANOS);
        }
    }
}
