package com.projectmodule.security;

import com.projectmodule.security.oidc.ClientKey;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to start a deployment where Workeasy360 sign-in is half-configured, or where it is meant to be the
 * only way in and is not configured.
 *
 * <p>Easy Project has its own email sign-in, so Workeasy360 sign-in is optional: a deployment that sets none
 * of the {@code WORKEASY360_*} settings starts with it switched off ("Continue with Workeasy360" is not
 * offered). A deployment that sets <em>some</em> of them is a mistake, and so is
 * {@code WORKEASY360_SIGN_IN_MODE=sso_only} without them (a deployment nobody could sign in to); failing at
 * start-up surfaces either before the first user does.
 *
 * <p>Only the {@code local} and {@code test} profiles may start with an incomplete configuration; there,
 * sign-in simply answers {@code unavailable}. The private key is parsed here too, so an unreadable or
 * wrong-type key stops the deployment rather than the first sign-in. No value is ever printed, only names.
 */
public class Workeasy360ConfigurationCheck implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(Workeasy360ConfigurationCheck.class);

    private final Workeasy360Properties properties;
    private final Environment environment;
    private final boolean ssoOnly;

    public Workeasy360ConfigurationCheck(Workeasy360Properties properties, Environment environment) {
        this(properties, environment, SignInOptions.SSO_OPTIONAL);
    }

    /** @param signInMode {@code sso_optional} or {@code sso_only} ({@code WORKEASY360_SIGN_IN_MODE}) */
    public Workeasy360ConfigurationCheck(Workeasy360Properties properties, Environment environment,
                                         String signInMode) {
        this.properties = properties;
        this.environment = environment;
        this.ssoOnly = SignInOptions.SSO_ONLY.equals(SignInOptions.validMode(signInMode));
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
        if (!missing.isEmpty() && !ssoOnly && nothingSet()) {
            // Email sign-in only: a complete deployment of its own.
            log.info("workeasy360.sign_in_off reason=not_configured");
            return;
        }
        if (!missing.isEmpty()) {
            throw new Workeasy360ConfigurationException(
                    (ssoOnly && nothingSet()
                            ? "WORKEASY360_SIGN_IN_MODE is sso_only but Workeasy360 sign-in is not configured"
                            : "Workeasy360 sign-in is only partly configured")
                            + "; missing: " + String.join(", ", missing), missing);
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

    /** None of the Workeasy360 settings is present (the product code has a default and does not count). */
    private boolean nothingSet() {
        return !Workeasy360Properties.hasText(properties.issuer())
                && !Workeasy360Properties.hasText(properties.clientId())
                && !Workeasy360Properties.hasText(properties.redirectUri())
                && !Workeasy360Properties.hasText(properties.keyId())
                && !Workeasy360Properties.hasText(properties.privateKeyFile())
                && !Workeasy360Properties.hasText(properties.privateKey())
                && !Workeasy360Properties.hasText(properties.postLogoutRedirectUri());
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

                        WORKEASY360_ISSUER                e.g. https://accounts.workeasy360.com
                        WORKEASY360_CLIENT_ID
                        WORKEASY360_REDIRECT_URI          https://<this host>/api/auth/callback/workeasy360
                        WORKEASY360_KEY_ID                the credential id printed at registration
                        WORKEASY360_PRIVATE_KEY_FILE      path of the PEM private key (or WORKEASY360_PRIVATE_KEY)

                    Optional: WORKEASY360_PRODUCT_CODE (default easy_project),
                    WORKEASY360_POST_LOGOUT_REDIRECT_URI and WORKEASY360_SIGN_IN_MODE
                    (sso_optional, the default, or sso_only).

                    Or, to run on email sign-in only, remove every WORKEASY360_* setting.

                    For local development start with SPRING_PROFILES_ACTIVE=local, which may run without them.""",
                    cause);
        }
    }
}
