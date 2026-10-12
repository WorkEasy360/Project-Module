package com.projectmodule.common.context;

/**
 * Inbound headers this module reads.
 *
 * <p>Only the correlation id. Caller identity is <strong>not</strong> carried in headers: the former
 * {@code X-User-Id} / {@code X-Org-Id} contract let anyone act as anyone and has been removed; identity
 * comes from the server-side session established by "Sign in with Workeasy360".
 */
public final class RequestContextHeaders {

    public static final String CORRELATION_ID = "X-Correlation-Id";

    private RequestContextHeaders() {
    }
}
