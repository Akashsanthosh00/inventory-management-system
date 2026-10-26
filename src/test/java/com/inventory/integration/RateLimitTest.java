package com.inventory.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.support.TestApi;
import com.inventory.support.TestMessagingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.inventory.support.TestApi.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMessagingConfig.class)
@TestPropertySource(properties = {"app.rate-limit.capacity=5", "app.rate-limit.auth-capacity=3"})
class RateLimitTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void authenticatedUserIsThrottledAfterCapacityIsExhausted() throws Exception {
        TestApi api = new TestApi(mvc, mapper);
        String name = unique("rl");
        String token = api.registerTenant(name, name + "@test.com").get("accessToken").asText(); // auth call 1/3

        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/products").header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void authEndpointsHaveTheirOwnStricterPerIpLimit() throws Exception {
        TestApi api = new TestApi(mvc, mapper);
        int tooMany = 0;
        for (int i = 0; i < 10; i++) { // brute-force style login attempts from one IP
            var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/auth/login").contentType("application/json")
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.7"); // dedicated client IP, isolated from other tests
                                return request;
                            })
                            .content("{\"email\":\"nobody@test.com\",\"password\":\"wrong\"}"))
                    .andReturn();
            if (result.getResponse().getStatus() == 429) {
                tooMany++;
            }
        }
        assertThat(tooMany).isGreaterThan(0);
    }
}
