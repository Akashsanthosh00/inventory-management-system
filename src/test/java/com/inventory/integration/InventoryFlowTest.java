package com.inventory.integration;

import com.inventory.support.TestMessagingConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Fast suite: H2 + in-memory cache + in-memory "broker". Runs in plain `mvn test`, no Docker required. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMessagingConfig.class)
class InventoryFlowTest extends AbstractFlowScenarios {
}
