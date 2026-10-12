package com.projectmodule.security.web;

import com.projectmodule.security.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsUtils;

/**
 * Writes must come from this application's own origin. The single-page app and the API are one origin, so a
 * write whose {@code Origin} is present and is not this origin, or whose {@code Sec-Fetch-Site} is anything
 * but {@code same-origin} (or {@code none}, a user-initiated navigation), is refused before CSRF is even
 * looked at.
 *
 * <p>This is defence in depth for the cookie-tossing case: a page on a sibling subdomain of the same site
 * can influence the cookies the browser sends, but it cannot make its requests same-origin. Browsers always
 * send {@code Origin} on cross-origin writes; a client that sends neither header (curl, server-to-server) is
 * not a browser and is left to the session and CSRF checks.
 */
public class SameOriginWritesFilter extends OncePerRequestFilter {

    public static final String CODE = "origin_not_allowed";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Set<String> ACCEPTED_FETCH_SITES = Set.of("same-origin", "none");

    private final ProblemResponses problems;

    public SameOriginWritesFilter(ProblemResponses problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod()) && !fromThisOrigin(request)) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Access denied",
                    "This request did not come from Easy Project's own pages.", Map.of("code", CODE));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean fromThisOrigin(HttpServletRequest request) {
        String site = request.getHeader("Sec-Fetch-Site");
        if (site != null && !ACCEPTED_FETCH_SITES.contains(site.toLowerCase(Locale.ROOT))) {
            return false;
        }
        String origin = request.getHeader("Origin");
        return origin == null || !CorsUtils.isCorsRequest(request);
    }
}
