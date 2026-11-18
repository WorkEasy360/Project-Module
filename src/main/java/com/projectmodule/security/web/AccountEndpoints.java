package com.projectmodule.security.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * The email-sign-in endpoints of the {@code account} package ({@code /api/v1/auth/**}, {@code /api/v1/workspace/**}),
 * as the security chain needs to tell them apart.
 *
 * <ul>
 *   <li><strong>Public</strong>: the sign-in page's configuration and the endpoints that issue or end an email
 *       sign-in's bearer token. Reachable without a session; none of them reads or changes the session cookie's
 *       identity, so they are exempt from CSRF (a token is returned in the body, never set as a cookie) and from
 *       the Workeasy360 entitlement check (they are sign-in endpoints, like {@code /api/auth/**}).
 *   <li><strong>Account only</strong>: endpoints about the caller's own email account or its workspace
 *       administration. A Workeasy360 sign-in has no email account here, so for it they answer 403
 *       {@code managed_in_workeasy360} instead of a misleading 401 (which the app would take for "signed out").
 *   <li><strong>Email sign-in</strong>: the endpoints refused in {@code sso_only} mode.
 * </ul>
 */
public final class AccountEndpoints {

    static final String AUTH = "/api/v1/auth/";

    private static final Set<String> PUBLIC_POSTS = Set.of(
            AUTH + "register", AUTH + "login", AUTH + "logout", AUTH + "google", AUTH + "legacy/claim",
            AUTH + "forgot-password", AUTH + "reset-password", AUTH + "invitation/preview",
            AUTH + "invitation/accept");

    private static final Set<String> EMAIL_SIGN_IN = Set.of(
            AUTH + "register", AUTH + "login", AUTH + "google", AUTH + "legacy/claim",
            AUTH + "forgot-password", AUTH + "reset-password", AUTH + "invitation/preview",
            AUTH + "invitation/accept");

    private AccountEndpoints() {
    }

    /** Reachable without a session: the sign-in page's configuration, and issuing or ending a bearer token. */
    public static boolean isPublic(HttpServletRequest request) {
        String path = path(request);
        String method = request.getMethod();
        return ("GET".equals(method) && (AUTH + "config").equals(path))
                || ("POST".equals(method) && PUBLIC_POSTS.contains(path));
    }

    /** About the caller's own email account or the administration of its workspace. */
    public static boolean isAccountOnly(HttpServletRequest request) {
        String path = path(request);
        if ((AUTH + "me").equals(path) || (AUTH + "change-password").equals(path)) {
            return true;
        }
        if (path.equals("/api/v1/workspace/invitations") || path.startsWith("/api/v1/workspace/invitations/")) {
            return true;
        }
        return path.startsWith("/api/v1/workspace/people/") && path.endsWith("/reset-link");
    }

    /** Starts an email sign-in (or creates the account for one): refused when Workeasy360 is the only way in. */
    public static boolean isEmailSignIn(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && EMAIL_SIGN_IN.contains(path(request));
    }

    static String path(HttpServletRequest request) {
        return request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
    }
}
