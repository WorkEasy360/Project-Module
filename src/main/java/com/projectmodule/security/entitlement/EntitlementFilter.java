package com.projectmodule.security.entitlement;

import com.projectmodule.security.ProblemResponses;
import com.projectmodule.security.Workeasy360Authentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps asking whether a signed-in person's organization may still use Easy Project, for every
 * {@code /api/v1/**} request, after authentication.
 *
 * <p>Answers: ended &rarr; 403 {@code workeasy360_access_ended} (with the provider's {@code reason});
 * allowed but not writable &rarr; writes get 403 {@code workeasy360_read_only}; Workeasy360 refusing this
 * product's own client &rarr; 403 {@code workeasy360_access_unverified}. The session itself stays valid, so
 * the person sees one "access has ended" screen and can sign out. {@code GET}, {@code HEAD} and
 * {@code OPTIONS} are reads; every other method is a write.
 *
 * <p>Not applied to {@code /api/auth/**} (sign-in, sign-out, the session read), the actuator health
 * endpoint or static files: only the API is behind it.
 */
public class EntitlementFilter extends OncePerRequestFilter {

    public static final String ACCESS_ENDED = "workeasy360_access_ended";
    public static final String READ_ONLY = "workeasy360_read_only";
    public static final String ACCESS_UNVERIFIED = "workeasy360_access_unverified";

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final EntitlementService entitlements;
    private final ProblemResponses problems;

    public EntitlementFilter(EntitlementService entitlements, ProblemResponses problems) {
        this.entitlements = entitlements;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        return !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof Workeasy360Authentication signedIn)) {
            // Nobody this application signed in. The authorization filter before this one refuses anonymous
            // requests; anything else that got this far (a foreign authentication type) is refused too, never
            // passed through.
            problems.write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Access denied",
                    "You are not allowed to do that.", Map.of("code", "forbidden"));
            return;
        }
        EntitlementService.Access access = entitlements.accessFor(signedIn.getPrincipal());
        boolean write = !READ_METHODS.contains(request.getMethod());
        if (access.refused()) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "workeasy360-access-unverified",
                    "Access could not be verified",
                    "Easy Project cannot confirm your organization's access with Workeasy360 at the moment. "
                            + "Try again in a few minutes; if this continues, contact support.",
                    Map.of("code", ACCESS_UNVERIFIED, "reason", access.reason()));
            return;
        }
        if (!access.allowed()) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "workeasy360-access-ended",
                    "Access has ended",
                    "Your access to Easy Project has ended. An owner can renew it in Workeasy360.",
                    Map.of("code", ACCESS_ENDED, "reason", access.reason()));
            return;
        }
        if (write && !access.writeAllowed()) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "workeasy360-read-only",
                    "Read-only",
                    "Your organization's Easy Project subscription is read-only at the moment: you can view "
                            + "your projects but not change them. An owner can resolve this in Workeasy360.",
                    Map.of("code", READ_ONLY, "reason", access.reason()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
