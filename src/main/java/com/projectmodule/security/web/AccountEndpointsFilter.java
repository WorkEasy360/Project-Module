package com.projectmodule.security.web;

import com.projectmodule.security.ProblemResponses;
import com.projectmodule.security.SignInOptions;
import com.projectmodule.security.Workeasy360Authentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Where the two ways of signing in meet the email-account endpoints ({@link AccountEndpoints}):
 *
 * <ul>
 *   <li>When Workeasy360 is the only way in ({@link SignInOptions#ssoOnly()}), starting an email sign-in or
 *       creating an account is refused with 403 {@code sign_in_with_workeasy360}.
 *   <li>A Workeasy360 sign-in has no email account here: its profile and password live in Workeasy360. The
 *       endpoints about the caller's own account, and workspace administration (invitations, reset links),
 *       answer it 403 {@code managed_in_workeasy360}. Without this they would answer 401, which the app would
 *       take for "signed out".
 * </ul>
 *
 * Neither code starts with {@code workeasy360_}: the app treats those as "access to Easy Project has ended".
 */
public class AccountEndpointsFilter extends OncePerRequestFilter {

    public static final String SIGN_IN_WITH_WORKEASY360 = "sign_in_with_workeasy360";
    public static final String MANAGED_IN_WORKEASY360 = "managed_in_workeasy360";

    private final SignInOptions options;
    private final ProblemResponses problems;

    public AccountEndpointsFilter(SignInOptions options, ProblemResponses problems) {
        this.options = options;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AccountEndpoints.path(request).startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (options.ssoOnly() && AccountEndpoints.isEmailSignIn(request)) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Sign in with Workeasy360",
                    "Sign in to Easy Project with Workeasy360: choose \"Continue with Workeasy360\".",
                    Map.of("code", SIGN_IN_WITH_WORKEASY360));
            return;
        }
        if (AccountEndpoints.isAccountOnly(request)
                && SecurityContextHolder.getContext().getAuthentication() instanceof Workeasy360Authentication) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Managed in Workeasy360",
                    "You signed in with Workeasy360: your profile, your password and who is in your organization "
                            + "are managed there.",
                    Map.of("code", MANAGED_IN_WORKEASY360));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
