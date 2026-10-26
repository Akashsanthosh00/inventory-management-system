package com.inventory.util;

import org.springframework.dao.ConcurrencyFailureException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Re-runs a unit of work when it loses a concurrency race: optimistic-lock failures (the main case), and also
 * lock timeouts / deadlock victims, which Spring reports as ConcurrencyFailureException subtypes.
 * <p>
 * IMPORTANT: the supplied work must start its own transaction (i.e. call a {@code @Transactional} method on
 * <i>another</i> bean) so that every attempt re-reads fresh data in a fresh transaction.
 */
public final class OptimisticRetry {

    public static final int DEFAULT_ATTEMPTS = 25;

    private OptimisticRetry() {
    }

    public static <T> T run(Supplier<T> work) {
        return run(DEFAULT_ATTEMPTS, work);
    }

    public static <T> T run(int maxAttempts, Supplier<T> work) {
        ConcurrencyFailureException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return work.get();
            } catch (ConcurrencyFailureException e) {
                last = e;
                backoff(attempt);
            }
        }
        throw last;
    }

    private static void backoff(int attempt) {
        try {
            // small randomized delay so competing workers do not collide in lock-step
            Thread.sleep(ThreadLocalRandom.current().nextInt(3, 10 + attempt * 2));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying", ie);
        }
    }
}
