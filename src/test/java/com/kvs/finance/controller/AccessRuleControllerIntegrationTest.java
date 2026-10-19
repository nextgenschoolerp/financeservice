package com.kvs.finance.controller;

import com.kvs.core.service.JwtTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AccessRuleController}: endpoint → allowed roles, read from the running service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessRuleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String bearer(String role) {
        return "Bearer " + jwtTokenService.generateToken("rules@kvs.com", Map.of("roles", List.of("ROLE_" + role)));
    }

    @Test
    @DisplayName("GET /api/v1/finance/access-rules - lists each endpoint with the roles its @PreAuthorize allows")
    void listsRules() throws Exception {
        mockMvc.perform(get("/api/v1/finance/access-rules").header("Authorization", bearer("REGISTRAR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThan(15)))
                .andExpect(jsonPath("$.data[?(@.method == 'POST' && @.path == '/api/v1/finance/fee-schedules/{scheduleId}/demands')].roles[*]",
                        containsInAnyOrder("CHIEF_ACCOUNTANT", "FINANCE_CONTROLLER", "SUPER_ADMIN")))
                .andExpect(jsonPath("$.data[?(@.method == 'POST' && @.path == '/api/v1/finance/receipts/counter')].roles[*]",
                        containsInAnyOrder("FINANCE_CASHIER", "TRUST_TREASURER", "CHIEF_ACCOUNTANT", "SUPER_ADMIN")));
    }

    @Test
    @DisplayName("GET /api/v1/finance/access-rules - 401 without a token")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/finance/access-rules")).andExpect(status().isUnauthorized());
    }
}
