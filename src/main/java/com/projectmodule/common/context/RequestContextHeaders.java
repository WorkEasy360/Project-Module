package com.projectmodule.common.context;

/**
 * Inbound headers that carry caller identity from the gateway.
 *
 * <p>These headers are trusted. They are only safe if this service is unreachable except
 * through the gateway that sets them; anything able to reach it directly could otherwise
 * assert any identity. Restricting ingress is a deployment control and forms part of the
 * contract with whoever owns Authentication.
 */
public final class RequestContextHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String ORGANIZATION_ID = "X-Org-Id";
    public static final String CORRELATION_ID = "X-Correlation-Id";

    private RequestContextHeaders() {
    }
}
