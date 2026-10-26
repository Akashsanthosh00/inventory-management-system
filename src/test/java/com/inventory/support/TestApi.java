package com.inventory.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Thin JSON helper around MockMvc so the scenarios read like API calls. */
public class TestApi {

    private final MockMvc mvc;
    private final ObjectMapper mapper;

    public TestApi(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    public JsonNode call(HttpMethod method, String url, String token, Object body, int expectedStatus) {
        try {
            MockHttpServletRequestBuilder builder = request(method, url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            if (body != null) {
                builder.content(mapper.writeValueAsString(body));
            }
            String json = mvc.perform(builder)
                    .andExpect(status().is(expectedStatus))
                    .andReturn().getResponse().getContentAsString();
            return json.isBlank() ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (AssertionError e) {
            throw new AssertionError(method + " " + url + " -> " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + url + " failed", e);
        }
    }

    public JsonNode get(String url, String token, int status) {
        return call(HttpMethod.GET, url, token, null, status);
    }

    public JsonNode post(String url, String token, Object body, int status) {
        return call(HttpMethod.POST, url, token, body, status);
    }

    public JsonNode put(String url, String token, Object body, int status) {
        return call(HttpMethod.PUT, url, token, body, status);
    }

    public JsonNode delete(String url, String token, int status) {
        return call(HttpMethod.DELETE, url, token, null, status);
    }

    /** Registers a brand-new business and returns the register response (token, tenantId, ...). */
    public JsonNode registerTenant(String businessName, String email) {
        return post("/api/auth/register", null, Map.of(
                "businessName", businessName, "adminName", "Owner", "email", email,
                "password", "Passw0rd!123"), 201);
    }

    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
