package com.inventory.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.config.CacheConfig;
import com.inventory.service.LowStockService;
import com.inventory.support.TestApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.inventory.support.TestApi.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end business scenarios, shared by:
 * <ul>
 *   <li>{@code InventoryFlowTest}  - H2 + in-memory messaging, runs with plain {@code mvn test} (no Docker)</li>
 *   <li>{@code FullStackFlowIT}    - real PostgreSQL + Redis + RabbitMQ via Testcontainers ({@code mvn verify})</li>
 * </ul>
 */
abstract class AbstractFlowScenarios {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired LowStockService lowStockService;
    @Autowired CacheManager cacheManager;

    TestApi api;

    @BeforeEach
    void initApi() {
        api = new TestApi(mvc, mapper);
    }

    /** A freshly registered business with one warehouse, one product (threshold 5) and a staff user. */
    record Shop(long tenantId, String adminToken, String staffToken, long warehouseId, long productId) {
    }

    Shop newShop(int initialStock) {
        String name = unique("shop");
        JsonNode reg = api.registerTenant(name, name + "@test.com");
        String admin = reg.get("accessToken").asText();
        long tenantId = reg.get("tenantId").asLong();

        String staffEmail = name + "-staff@test.com";
        api.post("/api/users", admin, Map.of("fullName", "Staff", "email", staffEmail,
                "password", "Passw0rd!123", "role", "STAFF"), 201);
        String staff = api.post("/api/auth/login", null,
                Map.of("email", staffEmail, "password", "Passw0rd!123"), 200).get("accessToken").asText();

        long warehouseId = api.post("/api/warehouses", admin,
                Map.of("name", "Main", "location", "Pune"), 201).get("id").asLong();
        long productId = api.post("/api/products", admin, Map.of("sku", "SKU-1", "name", "Basmati Rice 5kg",
                "price", 499.00, "lowStockThreshold", 5), 201).get("id").asLong();
        if (initialStock > 0) {
            adjustStock(admin, productId, warehouseId, initialStock);
        }
        return new Shop(tenantId, admin, staff, warehouseId, productId);
    }

    void adjustStock(String token, long productId, long warehouseId, int delta) {
        api.post("/api/stock/adjust", token, Map.of("productId", productId, "warehouseId", warehouseId,
                "delta", delta, "note", "test"), 200);
    }

    long placeOrder(Shop shop, String token, int quantity) {
        JsonNode order = api.post("/api/orders", token, Map.of(
                "customerName", "Asha", "customerEmail", "asha@test.com", "warehouseId", shop.warehouseId(),
                "items", List.of(Map.of("productId", shop.productId(), "quantity", quantity))), 202);
        assertThat(order.get("status").asText()).isEqualTo("PENDING");
        return order.get("id").asLong();
    }

    String orderStatus(Shop shop, long orderId) {
        return api.get("/api/orders/" + orderId, shop.adminToken(), 200).get("status").asText();
    }

    void awaitStatus(Shop shop, long orderId, String expected) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(orderStatus(shop, orderId)).isEqualTo(expected));
    }

    int stockQuantity(Shop shop) {
        JsonNode page = api.get("/api/stock?productId=" + shop.productId(), shop.adminToken(), 200);
        return page.get("content").get(0).get("quantity").asInt();
    }

    // ------------------------------------------------------------------------------------------------

    @Test
    void tenantsCannotSeeOrTouchEachOthersData() {
        Shop a = newShop(10);
        Shop b = newShop(10);

        api.get("/api/products/" + a.productId(), b.adminToken(), 404);                  // direct id access
        assertThat(api.get("/api/products?q=rice", b.adminToken(), 200).get("content")).hasSize(1); // only B's own
        assertThat(api.get("/api/products?q=rice", b.adminToken(), 200)
                .get("content").get(0).get("id").asLong()).isEqualTo(b.productId());
        api.post("/api/orders", b.adminToken(), Map.of("customerName", "X", "warehouseId", b.warehouseId(),
                "items", List.of(Map.of("productId", a.productId(), "quantity", 1))), 404); // A's product
        api.post("/api/orders", b.adminToken(), Map.of("customerName", "X", "warehouseId", a.warehouseId(),
                "items", List.of(Map.of("productId", b.productId(), "quantity", 1))), 404); // A's warehouse
        api.post("/api/stock/adjust", b.adminToken(), Map.of("productId", a.productId(),
                "warehouseId", b.warehouseId(), "delta", 5), 404);
        assertThat(api.get("/api/users", b.adminToken(), 200)).hasSize(2);               // B's admin + staff only
    }

    @Test
    void rolesAndAuthenticationAreEnforced() {
        Shop shop = newShop(10);

        api.get("/api/products", null, 401);
        api.get("/api/products", "garbage.token.value", 401);
        api.post("/api/products", shop.staffToken(), Map.of("sku", "S2", "name", "n", "price", 1), 403);
        api.post("/api/stock/adjust", shop.staffToken(), Map.of("productId", shop.productId(),
                "warehouseId", shop.warehouseId(), "delta", 1), 403);
        api.get("/api/users", shop.staffToken(), 403);

        api.get("/api/products", shop.staffToken(), 200);                                // staff may read...
        placeOrder(shop, shop.staffToken(), 1);                                         // ...and sell
        api.post("/api/auth/login", null, Map.of("email", "nobody@test.com", "password", "wrong-password"), 401);
    }

    @Test
    void validationAndDuplicateErrorsAreReportedClearly() {
        Shop shop = newShop(0);

        JsonNode invalid = api.post("/api/products", shop.adminToken(),
                Map.of("sku", "", "name", "n", "price", -1), 400);
        assertThat(invalid.get("errors").has("sku")).isTrue();
        assertThat(invalid.get("errors").has("price")).isTrue();

        api.post("/api/products", shop.adminToken(), Map.of("sku", "SKU-1", "name", "dup", "price", 1), 409);
        api.post("/api/stock/adjust", shop.adminToken(), Map.of("productId", shop.productId(),
                "warehouseId", shop.warehouseId(), "delta", -1), 422);                  // would go negative
    }

    @Test
    void orderIsProcessedAsynchronouslyThenCancelledAndStockRestored() {
        Shop shop = newShop(10);

        long orderId = placeOrder(shop, shop.staffToken(), 3);
        awaitStatus(shop, orderId, "CONFIRMED");
        assertThat(stockQuantity(shop)).isEqualTo(7);

        JsonNode audit = api.get("/api/stock/audit?productId=" + shop.productId(), shop.adminToken(), 200);
        List<String> reasons = new ArrayList<>();
        audit.get("content").forEach(e -> reasons.add(e.get("reason").asText()));
        assertThat(reasons).containsExactly("ORDER_FULFILLMENT", "RESTOCK");             // newest first
        assertThat(audit.get("content").get(0).get("quantityBefore").asInt()).isEqualTo(10);
        assertThat(audit.get("content").get(0).get("quantityAfter").asInt()).isEqualTo(7);
        assertThat(audit.get("content").get(0).get("performedBy").asText()).contains("@test.com");

        api.post("/api/orders/" + orderId + "/cancel", shop.staffToken(), null, 200);
        assertThat(orderStatus(shop, orderId)).isEqualTo("CANCELLED");
        assertThat(stockQuantity(shop)).isEqualTo(10);
        api.post("/api/orders/" + orderId + "/cancel", shop.staffToken(), null, 422);   // already cancelled
    }

    @Test
    void orderIsRejectedWhenStockIsInsufficientAndStockIsUntouched() {
        Shop shop = newShop(2);

        long orderId = placeOrder(shop, shop.staffToken(), 5);
        awaitStatus(shop, orderId, "REJECTED");

        JsonNode order = api.get("/api/orders/" + orderId, shop.adminToken(), 200);
        assertThat(order.get("failureReason").asText()).contains("Insufficient stock").contains("available 2");
        assertThat(stockQuantity(shop)).isEqualTo(2);
    }

    @Test
    void concurrentOrdersNeverOversellTheLastUnits() throws Exception {
        final int stock = 10;
        final int orders = 25;
        Shop shop = newShop(stock);

        ExecutorService clients = Executors.newFixedThreadPool(8);
        List<Future<Long>> placed = new ArrayList<>();
        for (int i = 0; i < orders; i++) {
            Callable<Long> call = () -> placeOrder(shop, shop.staffToken(), 1);
            placed.add(clients.submit(call));
        }
        List<Long> ids = new ArrayList<>();
        for (Future<Long> f : placed) {
            ids.add(f.get());
        }
        clients.shutdown();

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                assertThat(ids).allSatisfy(id -> assertThat(orderStatus(shop, id)).isIn("CONFIRMED", "REJECTED")));

        long confirmed = ids.stream().filter(id -> orderStatus(shop, id).equals("CONFIRMED")).count();
        long rejected = ids.stream().filter(id -> orderStatus(shop, id).equals("REJECTED")).count();
        assertThat(confirmed).as("confirmed orders").isEqualTo(stock);
        assertThat(rejected).as("rejected orders").isEqualTo(orders - stock);
        assertThat(stockQuantity(shop)).as("remaining stock").isZero();
    }

    @Test
    void lowStockAlertIsRaisedOnceAndResolvedAfterRestock() {
        Shop shop = newShop(3); // threshold is 5 -> already low

        assertThat(api.get("/api/stock/low", shop.adminToken(), 200)).hasSize(1);

        lowStockService.scan();
        lowStockService.scan(); // second run must not duplicate the alert
        JsonNode open = api.get("/api/alerts", shop.adminToken(), 200);
        assertThat(open.get("content")).hasSize(1);
        assertThat(open.get("content").get(0).get("sku").asText()).isEqualTo("SKU-1");
        assertThat(open.get("content").get(0).get("quantity").asInt()).isEqualTo(3);

        adjustStock(shop.adminToken(), shop.productId(), shop.warehouseId(), 50);
        lowStockService.scan();
        assertThat(api.get("/api/alerts", shop.adminToken(), 200).get("content")).isEmpty();
        assertThat(api.get("/api/alerts?resolved=true", shop.adminToken(), 200).get("content")).hasSize(1);
    }

    @Test
    void productLookupIsCachedAndEvictedOnUpdate() {
        Shop shop = newShop(0);
        String key = shop.tenantId() + ":" + shop.productId();
        var cache = cacheManager.getCache(CacheConfig.PRODUCTS);
        assertThat(cache).isNotNull();
        assertThat(cache.get(key)).isNull();

        api.get("/api/products/" + shop.productId(), shop.staffToken(), 200);
        assertThat(cache.get(key)).as("cached after first read").isNotNull();

        JsonNode updated = api.put("/api/products/" + shop.productId(), shop.adminToken(), Map.of(
                "sku", "SKU-1", "name", "Renamed Rice", "price", 550, "lowStockThreshold", 5), 200);
        assertThat(updated.get("name").asText()).isEqualTo("Renamed Rice");
        assertThat(cache.get(key)).as("evicted after update").isNull();

        assertThat(api.get("/api/products/" + shop.productId(), shop.staffToken(), 200)
                .get("name").asText()).isEqualTo("Renamed Rice");

        api.delete("/api/products/" + shop.productId(), shop.adminToken(), 204);
        assertThat(cache.get(key)).as("evicted after deactivate").isNull();
        assertThat(api.get("/api/products?q=rice", shop.adminToken(), 200).get("content")).isEmpty();
    }
}
