package com.projectmodule.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Writes RFC 9457 problem responses from the security filter chain.
 *
 * <p>Filters run ahead of the dispatcher, so the controller advice cannot shape their errors; this keeps
 * them in exactly the shape {@code GlobalExceptionHandler} uses (same {@code type} base, {@code title},
 * {@code traceId}) plus a machine-readable {@code code} where the client needs to branch on one.
 */
public final class ProblemResponses {

    public static final String TYPE_BASE = "https://errors.projectmodule/";

    private final ObjectMapper objectMapper;

    public ProblemResponses(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String typeSlug, String title, String detail, Map<String, Object> extra) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + typeSlug));
        problem.setTitle(title);
        problem.setProperty("traceId", CorrelationIdFilter.of(request));
        extra.forEach(problem::setProperty);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    /** 401: nobody is signed in (or the session ended). */
    public void unauthenticated(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, "identity-missing", "Caller identity missing",
                "Sign in with Workeasy360 to use Easy Project.", Map.of());
    }
}
