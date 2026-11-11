package com.projectmodule.common.context;

/**
 * Inbound headers that carry caller identity from the gateway.
 *
 * <p>The user and organization headers are ignored unless
 * {@code projectmodule.auth.trust-identity-headers} is true. They are only safe to trust if this
 * service is unreachable except through a gateway that sets them; anything able to reach it
 * directly could otherwise assert any identity.
 */
public final class RequestContextHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String ORGANIZATION_ID = "X-Org-Id";
    public static final String CORRELATION_ID = "X-Correlation-Id";

    private RequestContextHeaders() {
    }
}
