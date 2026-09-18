package com.projectmodule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the full Spring application context starts, with every bean wired.
 *
 * <p>Database autoconfiguration was excluded here while the skeleton had no entities and no
 * migrations, so there was nothing to persist and no schema to connect to. That stopped being
 * true once the P0 persistence slice landed, and became load-bearing once the Project CRUD
 * slice added a real bean graph (controller -> application service -> JPA repository) that the
 * context eagerly instantiates. The exclusion is removed and this now runs against a real
 * PostgreSQL 17 instance, exactly as originally planned.
 *
 * <p>Uses the same real-database gate as {@code ProjectPersistenceTest}: skipped unless
 * {@code PROJECTMODULE_TEST_DB_URL} is set, so the build stays green on a machine without a
 * database.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class ProjectModuleApplicationTests {

    @Test
    @DisplayName("application context loads")
    void contextLoads() {
        // Fails if the context cannot start.
    }
}
