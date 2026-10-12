package com.projectmodule.config;

import com.projectmodule.common.context.RequestContextHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the correlation id of a request: the caller's {@code X-Correlation-Id} when it is a sane
 * token, otherwise a fresh UUID. It is published as a request attribute and echoed on the response so a
 * caller can match its logs with ours.
 *
 * <p>This is all that is left of the old request-context filter. Who the caller <em>is</em> is no longer
 * read from headers at all: it comes from the authenticated session (see {@code RequestContextConfiguration}).
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Request attribute under which the correlation id is published. */
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._:\\-]{1,100}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(RequestContextHeaders.CORRELATION_ID);
        String correlationId = supplied != null && ACCEPTABLE.matcher(supplied.trim()).matches()
                ? supplied.trim() : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(RequestContextHeaders.CORRELATION_ID, correlationId);
        filterChain.doFilter(request, response);
    }

    /** The correlation id of the current request, or a fresh one outside any request. */
    public static String of(HttpServletRequest request) {
        Object attribute = request.getAttribute(ATTRIBUTE);
        return attribute instanceof String id ? id : UUID.randomUUID().toString();
    }
}
