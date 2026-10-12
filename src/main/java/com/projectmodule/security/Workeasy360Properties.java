package com.projectmodule.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything Easy Project needs to be a Workeasy360 client, from the environment
 * ({@code WORKEASY360_ISSUER}, {@code WORKEASY360_CLIENT_ID}, ...).
 *
 * <p>Only the issuer is configured about the provider: every endpoint comes from its discovery
 * document at run time.
 *
 * @param issuer            exact issuer identifier, e.g. {@code https://app.workeasy360.com}
 * @param clientId          this product's client id
 * @param redirectUri       the one registered callback; used verbatim, never derived from a request
 * @param keyId             {@code kid} of the registered public key (the credential id)
 * @param privateKeyFile    path of the PEM private key (preferred)
 * @param privateKey        the PEM itself, with literal backslash-n allowed for one-line variables
 * @param productCode       the product code the ID token must carry
 * @param postLogoutRedirectUri registered post-logout URI; when empty, "sign out of Workeasy360 too"
 *                          is not offered
 * @param discoveryTtl      how long the discovery document is reused
 * @param httpTimeout       connect/read timeout for every call to Workeasy360
 */
@ConfigurationProperties(prefix = "workeasy360")
public record Workeasy360Properties(
        String issuer,
        String clientId,
        String redirectUri,
        String keyId,
        String privateKeyFile,
        String privateKey,
        @DefaultValue("easy_project") String productCode,
        String postLogoutRedirectUri,
        @DefaultValue("1h") Duration discoveryTtl,
        @DefaultValue("10s") Duration httpTimeout) {

    /** Everything a sign-in needs is present. Says nothing about whether Workeasy360 is reachable. */
    public boolean isConfigured() {
        return hasText(issuer) && hasText(clientId) && hasText(redirectUri) && hasText(keyId)
                && (hasText(privateKeyFile) || hasText(privateKey));
    }

    public boolean signOutOfWorkeasy360Offered() {
        return isConfigured() && hasText(postLogoutRedirectUri);
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
