package com.inventory.integration;

/**
 * Same business scenarios as InventoryFlowTest, but through the real stack:
 * orders travel over RabbitMQ, product lookups are cached in Redis (JSON serialized), stock rows live in
 * PostgreSQL where optimistic locking is exercised by genuinely concurrent consumers.
 * <p>
 * Run with: {@code mvn verify}  (requires Docker).
 */
class FullStackFlowIT extends AbstractContainersIT {
}
