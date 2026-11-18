package com.projectmodule.common.context;

/**
 * Inbound headers this module reads.
 *
 * <p>Caller identity is <strong>not</strong> taken from these headers by default: the former
 * {@code X-User-Id} / {@code X-Org-Id} contract let anyone act as anyone. Identity comes from a
 * Workeasy360 sign-in session or an email sign-in's bearer token. The user and organization headers
 * are read only when {@code projectmodule.auth.trust-identity-headers} is true, which is safe only if
 * this service is unreachable except through a gateway that sets them; anything able to reach it
 * directly could otherwise assert any identity.
 */
public final class RequestContextHeaders {

    /** Read only in the trusted-gateway mode (see above). */
    public static final String USER_ID = "X-User-Id";
    /** Read only in the trusted-gateway mode (see above). */
    public static final String ORGANIZATION_ID = "X-Org-Id";
    public static final String CORRELATION_ID = "X-Correlation-Id";

    private RequestContextHeaders() {
    }
}
