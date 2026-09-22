package com.projectmodule.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;

class DataSourceConfigurationFailureAnalyzerTest {

    private final DataSourceConfigurationFailureAnalyzer analyzer =
            new DataSourceConfigurationFailureAnalyzer();

    /** The verbatim failure observed when the datasource environment variables are absent. */
    private static Throwable missingDatabaseConfiguration() {
        RuntimeException pool = new RuntimeException(
                "Driver org.postgresql.Driver claims to not accept jdbcUrl, ${PROJECTMODULE_DB_URL}");
        return new BeanCreationException("Error creating bean with name 'flywayInitializer'", pool);
    }

    @Test
    @DisplayName("names the missing settings instead of repeating the connection pool's wording")
    void explainsMissingDatabaseConfiguration() {
        FailureAnalysis analysis = analyzer.analyze(missingDatabaseConfiguration());

        assertThat(analysis).isNotNull();
        assertThat(analysis.getAction())
                .contains("PROJECTMODULE_DB_URL")
                .contains("PROJECTMODULE_DB_USERNAME")
                .contains("PROJECTMODULE_DB_PASSWORD")
                .contains("SPRING_PROFILES_ACTIVE=local");
        assertThat(analysis.getDescription()).contains("never falls back to a local database");
    }

    @Test
    @DisplayName("leaves every other startup failure to the normal diagnostics")
    void ignoresUnrelatedFailures() {
        assertThat(analyzer.analyze(new IllegalStateException("something else entirely"))).isNull();
        assertThat(analyzer.analyze(new BeanCreationException(
                "Driver org.postgresql.Driver claims to not accept jdbcUrl, jdbc:mysql://host/db")))
                .isNull();
    }
}
