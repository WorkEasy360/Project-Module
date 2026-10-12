package com.projectmodule.security;

import com.projectmodule.security.oidc.ClientKey;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to start any deployment where Workeasy360 sign-in is the only way in and is not fully
 * configured. Easy Project has no sign-in of its own (registered {@code sso_only}), so a deployment
 * without these settings would be an application nobody can use, or worse, one that had been quietly
 * left open; failing at start-up surfaces the mistake before the first user does.
 *
 * <p>Only the {@code local} and {@code test} profiles may start without the settings; there, sign-in
 * simply answers {@code unavailable}. The private key is parsed here too, so an unreadable or wrong-type
 * key stops the deployment rather than the first sign-in. No value is ever printed, only names.
 */
public class Workeasy360ConfigurationCheck implements InitializingBean {

    private final Workeasy360Properties properties;
    private final Environment environment;

    public Workeasy360ConfigurationCheck(Workeasy360Properties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (!Workeasy360Properties.hasText(properties.issuer())) {
            missing.add("WORKEASY360_ISSUER");
        }
        if (!Workeasy360Properties.hasText(properties.clientId())) {
            missing.add("WORKEASY360_CLIENT_ID");
        }
        if (!Workeasy360Properties.hasText(properties.redirectUri())) {
            missing.add("WORKEASY360_REDIRECT_URI");
        }
        if (!Workeasy360Properties.hasText(properties.keyId())) {
            missing.add("WORKEASY360_KEY_ID");
        }
        if (!Workeasy360Properties.hasText(properties.privateKeyFile())
                && !Workeasy360Properties.hasText(properties.privateKey())) {
            missing.add("WORKEASY360_PRIVATE_KEY_FILE (or WORKEASY360_PRIVATE_KEY)");
        }
        if (!missing.isEmpty()) {
            throw new Workeasy360ConfigurationException(
                    "Workeasy360 sign-in is not configured; missing: " + String.join(", ", missing), missing);
        }
        if (!isAbsoluteHttps(properties.issuer()) || !isAbsoluteHttps(properties.redirectUri())) {
            throw new Workeasy360ConfigurationException(
                    "WORKEASY360_ISSUER and WORKEASY360_REDIRECT_URI must be absolute https URLs.", List.of());
        }
        try {
            ClientKey.parse(ClientKey.readPem(properties.privateKeyFile(), properties.privateKey()),
                    properties.keyId());
        } catch (IllegalArgumentException invalidKey) {
            throw new Workeasy360ConfigurationException(
                    "The Workeasy360 private key cannot be used: " + invalidKey.getMessage(), List.of());
        }
    }

    private static boolean isAbsoluteHttps(String value) {
        try {
            URI uri = URI.create(value.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /** Start-up failed because Workeasy360 sign-in is not (correctly) configured. */
    public static class Workeasy360ConfigurationException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final List<String> missing;

        public Workeasy360ConfigurationException(String message, List<String> missing) {
            super(message);
            this.missing = List.copyOf(missing);
        }

        public List<String> missing() {
            return missing;
        }
    }

    /** Turns the failure into the settings to provide, instead of a stack trace. */
    public static class Analyzer extends AbstractFailureAnalyzer<Workeasy360ConfigurationException> {

        @Override
        protected FailureAnalysis analyze(Throwable rootFailure, Workeasy360ConfigurationException cause) {
            return new FailureAnalysis(
                    cause.getMessage() + "\n\nEasy Project has no sign-in of its own: people sign in with Workeasy360, "
                            + "so the application refuses to start without the settings that make that work.",
                    """
                    Register Easy Project in Workeasy360 (docs/workeasy360-sso.md), then provide:

                        WORKEASY360_ISSUER                e.g. https://app.workeasy360.com
                        WORKEASY360_CLIENT_ID
                        WORKEASY360_REDIRECT_URI          https://<this host>/api/auth/callback/workeasy360
                        WORKEASY360_KEY_ID                the credential id printed at registration
                        WORKEASY360_PRIVATE_KEY_FILE      path of the PEM private key (or WORKEASY360_PRIVATE_KEY)

                    Optional: WORKEASY360_PRODUCT_CODE (default easy_project) and
                    WORKEASY360_POST_LOGOUT_REDIRECT_URI.

                    For local development start with SPRING_PROFILES_ACTIVE=local, which may run without them.""",
                    cause);
        }
    }
}
