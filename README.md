# Multi-Tenant Inventory & Order Management System

A REST backend for small businesses (kirana stores, small e-commerce sellers). Many businesses ("tenants") share one
deployment; each sees only its own products, warehouses, stock, orders and users.

**Stack:** Java 21 · Spring Boot 3.3 · Spring Web/Validation · Spring Security + JWT · Spring Data JPA/Hibernate ·
PostgreSQL · Redis · RabbitMQ · `@Scheduled` · Bucket4j · springdoc/Swagger · Lombok · JUnit 5 · Mockito · Testcontainers

---

## 1. Run it

### Prerequisites
JDK 21, Maven 3.9+ (IntelliJ bundles Maven), Docker Desktop.

### Start the infrastructure
```bash
docker compose up -d postgres redis rabbitmq
```

### Start the app
* **IntelliJ:** `File > Open` the project folder (it detects `pom.xml`), wait for Maven import, set Project SDK to 21,
  enable *Settings > Build > Compiler > Annotation Processors > Enable annotation processing* (needed for Lombok),
  then run `InventoryApplication`.
* **CLI:** `mvn spring-boot:run`

### Or run everything in Docker
```bash
docker compose up --build
```

| What | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| RabbitMQ console | http://localhost:15672 (guest / guest) |

`http/inventory.http` is a ready-made request collection for IntelliJ's built-in HTTP client (it stores the token and
ids for you). In Swagger: call `POST /api/auth/register`, copy `accessToken`, click **Authorize**.

### Configuration (environment variables)
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`,
`RABBIT_PASSWORD`, `JWT_SECRET` (Base64, at least 256 bit), `DDL_AUTO`. Defaults match `docker-compose.yml`.
**Change `JWT_SECRET` for any real deployment** (`openssl rand -base64 48`).

---

## 2. Tests

```bash
mvn test       # unit tests + fast end-to-end suite on H2 (no Docker needed)
mvn verify     # additionally runs *IT classes with Testcontainers: real PostgreSQL + Redis + RabbitMQ (Docker needed)
```

| Class | Kind | Proves |
|---|---|---|
| `OrderFulfillmentServiceTest` | Mockito unit | all-or-nothing stock deduction, reject, idempotency, cancel restocks |
| `ProductServiceTest`, `LowStockServiceTest` | Mockito unit | duplicate SKU, tenant-scoped lookup, alert de-duplication / auto-resolve |
| `JwtServiceTest` | unit | token round trip, tampering, expiry |
| `CacheSerializationTest` | unit | Redis JSON value round trip |
| `InventoryFlowTest` | Spring Boot + H2 | tenant isolation, roles, async order flow, **25 concurrent orders for 10 units never oversell**, alerts, cache eviction |
| `RateLimitTest` | Spring Boot + H2 | HTTP 429 + `Retry-After`, stricter limit on `/api/auth/**` |
| `FullStackFlowIT` | Testcontainers | same scenarios against real Postgres, Redis, RabbitMQ |

---

## 3. Architecture

```
 client ──HTTP+JWT──> Controllers ──> Services ──> JPA repositories ──> PostgreSQL
                         │                │
        RateLimitFilter  │                ├── @Cacheable product lookups ──> Redis
        JwtAuthFilter    │                │
                         │                └── "order placed" event ──(after commit)──> RabbitMQ
                         │                                                    │
                         │                    order.placed.queue ──> OrderMessageListener
                         │                        │  └─ failures: retry x3 -> order.placed.dlq
                         │                        └─> OrderProcessor ──> OrderFulfillmentService (transaction)
                         │                                   │   optimistic-lock retry
                         │                                   └─ CONFIRMED / REJECTED ──> order.status ──> notifications
                         └── @Scheduled LowStockScheduler ──> low_stock_alerts
```

### Multi-tenancy (shared database, `tenant_id` discriminator)
* `Tenant` is created together with its first `ADMIN` at `POST /api/auth/register`.
* The **tenant id is a claim inside the signed JWT** and is read from the authenticated principal, never from the
  request body, so a client cannot choose which tenant it acts on.
* Every tenant-owned table has a `tenant_id` column and **every repository query filters by it**
  (`findByIdAndTenantId`, ...). Accessing another tenant's id returns **404**, not 403, so existence does not leak.
* The Redis cache key includes the tenant id (`inventory:products::{tenantId}:{productId}`).
* Trade-off to mention in interviews: simplest and cheapest model. Alternatives are schema-per-tenant or
  database-per-tenant (stronger isolation, more operational cost) and PostgreSQL row-level security as a
  defence-in-depth layer.

### Preventing overselling (optimistic locking)
`StockLevel` has a JPA `@Version` column. Two workers deducting from the same row both read `version = N`; the first
`UPDATE ... WHERE id=? AND version=N` succeeds, the second affects 0 rows and Hibernate throws an optimistic-lock
exception. `OrderProcessor` re-runs the *whole* transaction (fresh read, fresh decision) up to 25 times with a small
random back-off. Result: with 10 units and 25 simultaneous orders, exactly 10 are confirmed and 15 rejected, and stock
ends at 0 (`concurrentOrdersNeverOversellTheLastUnits`). Orders have their own `@Version` so a cancel cannot race with
fulfilment. Why optimistic and not `SELECT ... FOR UPDATE`? Contention per row is low in this domain, and optimistic
locking holds no DB locks while the application thinks.

### Async order processing
`POST /api/orders` validates, stores the order as `PENDING` and returns **202**. A `@TransactionalEventListener
(AFTER_COMMIT)` publishes the message to RabbitMQ, so the consumer can never see an order that is not yet committed
(or one that rolled back). The consumer deducts stock all-or-nothing, writes audit rows, sets `CONFIRMED` / `REJECTED`
and emits an `order.status` event for the notification consumer. The handler is idempotent (non-`PENDING` orders are
skipped), which matters because brokers deliver at least once. After 3 failed attempts a message is dead-lettered to
`order.placed.dlq`.

### Audit log
Each stock movement (restock, manual adjustment, order fulfilment, order cancellation) writes a `stock_audit_logs` row
**in the same transaction** with before / delta / after, reason, reference (order number) and the acting user.
`GET /api/stock/audit`.

### Low-stock alerts
`LowStockScheduler` (`@Scheduled`, cron in `app.low-stock.cron`) calls `LowStockService.scan()`: one alert per
product+warehouse at or below `lowStockThreshold`, raised once while it stays low, auto-resolved after restock.
Alerts are stored (`GET /api/alerts`) and passed to `NotificationService` (currently logs; plug in SES/SNS/Slack there).
With several app instances add ShedLock so only one node runs the job.

### Security
Stateless JWT (HS256, jjwt). `ADMIN` can manage users, products, warehouses and stock; `STAFF` can read and sell
(place / cancel orders). BCrypt password hashes. 401 / 403 / 409 / 422 / 429 are returned as RFC 7807 problem JSON.

### Rate limiting
Bucket4j token buckets in a servlet filter: 100 requests/min per authenticated user (per IP when anonymous) and 10/min
per IP on `/api/auth/**` against brute force. In-memory (single instance); for a cluster use Bucket4j's Redis backend.

---

## 4. API summary

| Method & path | Role | Notes |
|---|---|---|
| `POST /api/auth/register` | public | new business + admin, returns JWT |
| `POST /api/auth/login` | public | returns JWT |
| `POST/GET /api/users` | ADMIN | staff management inside own tenant |
| `POST/PUT/GET /api/warehouses` | ADMIN (write) | |
| `POST/PUT/DELETE /api/products`, `GET /api/products[/{id}]` | ADMIN (write) | `GET /{id}` cached in Redis; `q`, `page`, `size`; delete = soft delete |
| `POST /api/stock/adjust` | ADMIN | `delta` > 0 receive, < 0 write off |
| `GET /api/stock`, `/api/stock/low`, `/api/stock/audit` | any | filters `productId`, `warehouseId` |
| `POST /api/orders` | any | 202 + PENDING |
| `GET /api/orders[/{id}]`, `POST /api/orders/{id}/cancel` | any | list filter `status` |
| `GET /api/alerts` | any | `resolved=false|true` |

---

## 5. Deploying to AWS (outline)
Build the image (`Dockerfile`), push to ECR, run on ECS Fargate behind an ALB with health check
`/actuator/health`. Use RDS PostgreSQL, ElastiCache Redis and Amazon MQ for RabbitMQ; inject the `DB_*`, `REDIS_*`,
`RABBIT_*` variables and `JWT_SECRET` from Secrets Manager / SSM.

## 6. Known limitations / next steps
* Schema is created by Hibernate `ddl-auto=update` for convenience; use Flyway or Liquibase with `validate` in production.
* JWTs are not revocable before expiry (no refresh-token or blacklist flow).
* One warehouse per order, no partial fulfilment, no transfers between warehouses yet.
* Possible additions: outbox pattern for guaranteed publishing, ShedLock, Prometheus metrics, React frontend.
