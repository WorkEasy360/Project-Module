package com.projectmodule.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.common.context.BearerTokenAuthenticator;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.context.RequestContextFactory;
import com.projectmodule.common.context.RequestContextHeaders;
import com.projectmodule.common.exception.ErrorType;
import com.projectmodule.common.exception.ValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes who is calling and attaches a {@link RequestContext} to the request.
 *
 * <p>Identity comes from a verified session token in the {@code Authorization} header. The
 * {@code X-User-Id} and {@code X-Org-Id} headers can be set by anyone able to reach this service,
 * so they are ignored unless a deployment has a gateway that sets them and has said so through
 * {@code projectmodule.auth.trust-identity-headers}. A missing or invalid token is not rejected
 * here: the request continues with no identity and protected operations answer 401, which keeps
 * sign-in, health checks and the UI's own files reachable.
 *
 * <p>The context is stored as a request attribute rather than a thread local. Thread locals do
 * not survive the thread handoffs that virtual threads and asynchronous dispatch make routine,
 * and they leak between requests when a pool reuses a thread without cleanup.
 *
 * <p>A malformed identifier is answered here with a problem response. Filters run ahead of the
 * dispatcher, so a controller advice cannot see failures at this point.
 */
public class RequestContextFilter extends OncePerRequestFilter {

    /** Request attribute under which the context is published. */
    public static final String ATTRIBUTE = RequestContext.class.getName();

    private static final String TYPE_BASE = "https://errors.projectmodule/";

    private static final String BEARER_PREFIX = "Bearer ";

    private final RequestContextFactory factory;
    private final ObjectMapper objectMapper;
    private final BearerTokenAuthenticator authenticator;
    private final boolean trustIdentityHeaders;

    public RequestContextFilter(RequestContextFactory factory,
                                ObjectMapper objectMapper,
                                BearerTokenAuthenticator authenticator,
                                boolean trustIdentityHeaders) {
        this.factory = factory;
        this.objectMapper = objectMapper;
        this.authenticator = authenticator;
        this.trustIdentityHeaders = trustIdentityHeaders;
    }

    /** The token in an {@code Authorization} header value, or null if it does not carry one. */
    public static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private RequestContext resolveContext(HttpServletRequest request) {
        String correlationId = request.getHeader(RequestContextHeaders.CORRELATION_ID);
        String token = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token != null) {
            // An invalid token never falls back to the headers: it means anonymous, so a client
            // cannot present a dead session alongside someone else's id.
            return authenticator.authenticate(token)
                    .map(identity -> factory.authenticated(identity, correlationId))
                    .orElseGet(() -> factory.anonymous(correlationId));
        }
        if (trustIdentityHeaders) {
            return factory.fromHeaders(
                    request.getHeader(RequestContextHeaders.USER_ID),
                    request.getHeader(RequestContextHeaders.ORGANIZATION_ID),
                    correlationId);
        }
        return factory.anonymous(correlationId);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        RequestContext context;
        try {
            context = resolveContext(request);
        } catch (ValidationException exception) {
            writeProblem(response, exception.getMessage());
            return;
        }

        request.setAttribute(ATTRIBUTE, context);
        // Echoed so a caller can correlate its own logs with ours.
        response.setHeader(RequestContextHeaders.CORRELATION_ID, context.correlationId());

        filterChain.doFilter(request, response);
    }

    private void writeProblem(HttpServletResponse response, String detail) throws IOException {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setType(URI.create(TYPE_BASE + ErrorType.VALIDATION_FAILED.slug()));
        problemDetail.setTitle(ErrorType.VALIDATION_FAILED.title());

        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }
}
