package com.projectmodule.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which ways in this deployment offers, as the sign-in page and the security chain need to know them.
 *
 * <p>Easy Project has its own email sign-in, and "Sign in with Workeasy360" when the {@code WORKEASY360_*}
 * settings are present. The Workeasy360 integration guide (section 6b) calls a product with both
 * {@code sso_optional}: its sign-in page offers both and it never starts a Workeasy360 sign-in by itself. A
 * deployment can instead be registered {@code sso_only} ({@code WORKEASY360_SIGN_IN_MODE=sso_only}): then the
 * app starts Workeasy360 sign-in by itself for a browser with no session, and the email sign-in endpoints
 * refuse new sign-ins ({@code sign_in_with_workeasy360}). Sessions already open stay valid until they end.
 *
 * @param workeasy360Enabled every setting a Workeasy360 sign-in needs is present
 * @param signInMode         {@code sso_optional} (the default) or {@code sso_only}
 */
public record SignInOptions(boolean workeasy360Enabled, String signInMode) {

    public static final String SSO_OPTIONAL = "sso_optional";
    public static final String SSO_ONLY = "sso_only";

    public SignInOptions {
        signInMode = validMode(signInMode);
    }

    /** Workeasy360 is the only way in: configured, and registered {@code sso_only}. */
    public boolean ssoOnly() {
        return workeasy360Enabled && SSO_ONLY.equals(signInMode);
    }

    /** What {@code GET /api/v1/auth/config} tells the sign-in page. Holds no secret. */
    public Map<String, Object> describe() {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("enabled", workeasy360Enabled);
        description.put("signInMode", signInMode);
        return description;
    }

    /** The mode, defaulted when blank. Anything else is a configuration error and stops the start. */
    public static String validMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return SSO_OPTIONAL;
        }
        String trimmed = mode.trim();
        if (SSO_OPTIONAL.equals(trimmed) || SSO_ONLY.equals(trimmed)) {
            return trimmed;
        }
        throw new IllegalArgumentException(
                "WORKEASY360_SIGN_IN_MODE must be sso_optional or sso_only.");
    }
}
