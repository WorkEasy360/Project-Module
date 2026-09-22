package com.projectmodule.config;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;

/**
 * Explains the startup failure that occurs when the database environment variables are absent.
 *
 * <p>{@code application.yml} deliberately declares the datasource with no defaults
 * ({@code url: ${PROJECTMODULE_DB_URL}}) so a deployment can never silently fall back to a
 * developer's local database. The cost is an unhelpful diagnostic: the unresolved placeholder is
 * handed to the connection pool verbatim and the application dies with
 * {@code Driver org.postgresql.Driver claims to not accept jdbcUrl, ${PROJECTMODULE_DB_URL}},
 * which names neither the missing variable nor the fix.
 *
 * <p>This analyzer detects exactly that case and replaces the message with the variables to set.
 * It runs only when startup has already failed, so it cannot affect a healthy application, and it
 * returns {@code null} for every other failure so the normal diagnostics still apply. No value is
 * read or printed — only the names of the settings.
 */
public class DataSourceConfigurationFailureAnalyzer implements FailureAnalyzer {

    /** Hikari's wording when the JDBC URL it was given is not a URL the driver understands. */
    private static final String REJECTED_URL = "claims to not accept jdbcUrl";

    /** An unresolved Spring placeholder that reached the pool as literal text. */
    private static final String UNRESOLVED_PLACEHOLDER = "${";

    @Override
    public FailureAnalysis analyze(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null
                    && message.contains(REJECTED_URL)
                    && message.contains(UNRESOLVED_PLACEHOLDER)) {
                return new FailureAnalysis(description(), action(), cause);
            }
        }
        return null;
    }

    private static String description() {
        return "The database connection settings are missing, so the JDBC URL reached the "
                + "connection pool as an unresolved placeholder instead of a real address. "
                + "This application never falls back to a local database, so that a deployment "
                + "with no configuration fails here rather than quietly connecting somewhere "
                + "unintended.";
    }

    private static String action() {
        return """
                Provide the database connection settings through the environment.

                For a deployment (Elastic Beanstalk environment properties, container
                environment, or the shell that starts the jar), set all three:

                    PROJECTMODULE_DB_URL       e.g. jdbc:postgresql://<host>:5432/<database>
                    PROJECTMODULE_DB_USERNAME
                    PROJECTMODULE_DB_PASSWORD

                The equivalent Spring names (SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME,
                SPRING_DATASOURCE_PASSWORD) are also honoured and take precedence.

                For local development, start with the 'local' profile instead, which already
                points at jdbc:postgresql://localhost:5432/projectmodule as user 'postgres' and
                needs only the password:

                    SPRING_PROFILES_ACTIVE=local
                    PROJECTMODULE_DB_PASSWORD=<your local password>

                Never commit any of these values.""";
    }
}
