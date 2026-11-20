package com.siose.ff.logistics.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The area migrates its own tables, with a history of its own (decision 3), whichever application
 * runs it. The entities are then checked against those tables at startup (ddl-auto validate), so
 * a context that starts at all means the scripts and the entities agree.
 */
@SpringBootTest
class RoutingMigrationsTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theAreaRecordsItsVersionsInItsOwnHistory() {
        List<String> versions = jdbc.queryForList(
                "select \"version\" from routing_flyway_history where \"type\" = 'SQL' order by \"installed_rank\"",
                String.class);

        assertThat(versions).containsExactly("1", "2", "3");
    }
}
