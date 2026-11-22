package com.siose.ff.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The process migrates its own schema and answers the probes. */
@SpringBootTest
@AutoConfigureMockMvc
class ReportingApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migratesGenerationAndKeepsTheOutboxBesideIt() {
        Integer version = jdbc.queryForObject(
                "select max(\"version\") from \"generation\".\"generation_flyway_history\" where \"type\" = 'SQL'",
                Integer.class);
        assertThat(version).isEqualTo(1);
        Integer delivery = jdbc.queryForObject(
                "select max(\"version\") from \"delivery\".\"delivery_flyway_history\" where \"type\" = 'SQL'",
                Integer.class);
        assertThat(delivery).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from reporting.event_publication", Integer.class)).isNotNull();
    }

    @Test
    void answersTheProbes() throws Exception {
        for (String path : new String[] {"/actuator/health/liveness", "/actuator/health/readiness",
                "/actuator/health/release"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        }
    }
}
