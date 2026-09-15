package com.projectmodule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring application context starts and the component scan is sound.
 *
 * <p>Database autoconfiguration is excluded for now because the skeleton has no entities and
 * no migrations, so there is nothing to persist and no schema to connect to. This keeps the
 * build runnable on any machine and in CI without a database.
 *
 * <p>This exclusion is temporary. It is removed once the first entities and Flyway migrations
 * land, at which point context tests run against a real PostgreSQL instance.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class ProjectModuleApplicationTests {

    @Test
    @DisplayName("application context loads")
    void contextLoads() {
        // Fails if the context cannot start.
    }
}
