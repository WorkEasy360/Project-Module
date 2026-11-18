package com.projectmodule.security.web;

import com.projectmodule.common.context.AuthenticatedIdentity;
import com.projectmodule.common.context.BearerTokenAuthenticator;
import com.projectmodule.common.context.RequestContextHeaders;
import com.projectmodule.common.exception.ErrorType;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.security.AccountTokenAuthentication;
import com.projectmodule.security.ProblemResponses;
import com.projectmodule.security.TrustedGatewayAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Email sign-in on the business API: a request to {@code /api/v1/**} that carries
 * {@code Authorization: Bearer <token>} is the token's account, for this request only.
 *
 * <ul>
 *   <li>A bearer token, valid or not, decides the request. A valid one is its account; an unknown, expired or
 *       revoked one means anonymous (the API answers 401). The session cookie, if the browser also sent one, is
 *       not used for that request, so a client cannot present a dead token alongside someone else's session.
 *       The authentication is never stored in a session.
 *   <li>Without a bearer token, and only when the deployment trusts a gateway
 *       ({@code projectmodule.auth.trust-identity-headers}, off by default), {@code X-User-Id} /
 *       {@code X-Org-Id} are the identity. A malformed id is answered 400 here. Otherwise those headers are
 *       never read.
 *   <li>Anything else is left to the Workeasy360 session (or nobody).
 * </ul>
 *
 * A request identified this way carries its credential explicitly rather than in a cookie, so CSRF and the
 * same-origin check, which protect the cookie, do not apply to it ({@link #carriesExplicitCredential}).
 * It runs after the shared sign-in record check ({@code SignInRevocationFilter}) and before CSRF.
 */
public class AccountTokenFilter extends OncePerRequestFilter {

    private final Supplier<BearerTokenAuthenticator> authenticator;
    private final boolean trustIdentityHeaders;
    private final ProblemResponses problems;

    /**
     * @param authenticator        the account package's token check; supplies null when the application has
     *                             none (then every token is unknown)
     * @param trustIdentityHeaders whether a gateway's {@code X-User-Id} / {@code X-Org-Id} are trusted
     */
    public AccountTokenFilter(Supplier<BearerTokenAuthenticator> authenticator, boolean trustIdentityHeaders,
                              ProblemResponses problems) {
        this.authenticator = authenticator;
        this.trustIdentityHeaders = trustIdentityHeaders;
        this.problems = problems;
    }

    /**
     * The request is identified by what it carries (a bearer token, or a trusted gateway's identity headers),
     * never by the session cookie: CSRF and the same-origin check do not apply to it.
     */
    public static boolean carriesExplicitCredential(HttpServletRequest request, boolean trustIdentityHeaders) {
        if (!isBusinessApi(request)) {
            return false;
        }
        if (BearerTokenAuthenticator.tokenFrom(request.getHeader(HttpHeaders.AUTHORIZATION)) != null) {
            return true;
        }
        return trustIdentityHeaders && carriesIdentityHeaders(request);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isBusinessApi(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = BearerTokenAuthenticator.tokenFrom(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token != null) {
            BearerTokenAuthenticator accounts = authenticator.get();
            Optional<AuthenticatedIdentity> identity = accounts == null ? Optional.empty() : accounts.authenticate(token);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            identity.ifPresent(account -> context.setAuthentication(new AccountTokenAuthentication(account)));
            SecurityContextHolder.setContext(context);
        } else if (trustIdentityHeaders && carriesIdentityHeaders(request)) {
            UUID userId;
            UUID organizationId;
            try {
                userId = parse(request.getHeader(RequestContextHeaders.USER_ID), true);
                organizationId = parse(request.getHeader(RequestContextHeaders.ORGANIZATION_ID), false);
            } catch (ValidationException malformed) {
                problems.write(request, response, HttpStatus.BAD_REQUEST, ErrorType.VALIDATION_FAILED.slug(),
                        ErrorType.VALIDATION_FAILED.title(), malformed.getMessage(), Map.of());
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new TrustedGatewayAuthentication(userId, organizationId));
            SecurityContextHolder.setContext(context);
        }
        filterChain.doFilter(request, response);
    }

    private static UUID parse(String header, boolean user) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return user ? ExternalUserId.fromString(header.trim()).value() : OrganizationId.fromString(header.trim()).value();
    }

    private static boolean carriesIdentityHeaders(HttpServletRequest request) {
        return hasText(request.getHeader(RequestContextHeaders.USER_ID))
                || hasText(request.getHeader(RequestContextHeaders.ORGANIZATION_ID));
    }

    private static boolean isBusinessApi(HttpServletRequest request) {
        return AccountEndpoints.path(request).startsWith("/api/v1/");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
