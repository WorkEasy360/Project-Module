package com.projectmodule.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the gateway-supplied identity headers and attaches a {@link RequestContext} to the
 * request.
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

    private final RequestContextFactory factory;
    private final ObjectMapper objectMapper;

    public RequestContextFilter(RequestContextFactory factory, ObjectMapper objectMapper) {
        this.factory = factory;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        RequestContext context;
        try {
            context = factory.fromHeaders(
                    request.getHeader(RequestContextHeaders.USER_ID),
                    request.getHeader(RequestContextHeaders.ORGANIZATION_ID),
                    request.getHeader(RequestContextHeaders.CORRELATION_ID));
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
