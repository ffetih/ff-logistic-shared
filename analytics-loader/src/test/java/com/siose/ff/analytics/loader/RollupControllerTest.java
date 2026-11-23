package com.siose.ff.analytics.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The recount POST is the scheduled recount, and it asks only for a signed-in caller.
 */
@AutoConfigureMockMvc
@EnabledIf(AnalyticsTestSupport.CONDITION)
class RollupControllerTest extends AnalyticsTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aCallerWithoutATokenIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/rollups")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSignedInCallerRecountsWhatChangedAndASecondCallChangesNothing() throws Exception {
        String tenant = "t-" + UUID.randomUUID().toString().substring(0, 8);
        store.store(new ShipmentDeliveredMessage(UUID.randomUUID(), tenant, "SHP-1", null, 10, 1,
                Instant.parse("2026-11-04T08:00:00Z")));

        mockMvc.perform(post("/api/v1/rollups").with(jwt().jwt(token -> token.subject("planner")
                        .claim("tenant_id", tenant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantMonths").value(1));

        assertThat(jdbc.queryForMap("""
                select delivered_shipments, total_distance_km, saved_co2e_kg from tenant_delivery_monthly
                where tenant_id = ? and delivery_month = ?""", tenant, LocalDate.parse("2026-11-01")))
                .containsEntry("delivered_shipments", 1)
                .containsEntry("total_distance_km", new BigDecimal("10.000"))
                .containsEntry("saved_co2e_kg", new BigDecimal("1.000"));

        mockMvc.perform(post("/api/v1/rollups").with(jwt().jwt(token -> token.subject("planner")
                        .claim("tenant_id", tenant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantMonths").value(0));
    }
}
