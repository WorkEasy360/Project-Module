package com.projectmodule.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

/**
 * "This browser signed out of Easy Project": the marker that stops the single-page app from starting
 * Workeasy360 sign-in by itself (integration guide section 6b, rule 2).
 *
 * <p>Opening Easy Project with no session of its own starts a sign-in automatically, so that a person who is
 * already signed in to Workeasy360 arrives signed in without a password. Without this marker a sign-out would
 * undo itself: the very next page load would sign the person straight back in. Both sign-outs ({@code POST
 * /api/auth/logout}, which "Sign out of Easy Project" is, and the first half of "Sign out of Easy Project
 * and Workeasy360") set it here, on the logout response. The registered post-logout address of Easy Project
 * is its root, so after "...and Workeasy360" the browser comes back to {@code /}, finds the marker, and shows
 * the sign-in page instead of bouncing back into a sign-in at Workeasy360.
 *
 * <p>The marker is cleared by a successful sign-in (never by a failed one), so it holds until the person
 * signs in to Easy Project again. It is a <em>session cookie</em> (no {@code Max-Age}: it ends with the
 * browser session and is shared by every tab), {@code SameSite=Lax}, path {@code /}, and deliberately
 * <em>not</em> {@code HttpOnly}: the single-page app reads it to decide whether to start a sign-in. It holds
 * no information and grants nothing, so a forged or planted marker can only make the app show its sign-in
 * page instead of starting one. Over https the cookie carries the {@code __Host-} prefix, which a sibling
 * {@code *.workeasy360.com} site cannot plant (the browser refuses a {@code __Host-} cookie with a
 * {@code Domain}); and because such a site could plant an <em>unprefixed</em> {@code ep_signed_out} with
 * {@code Domain=workeasy360.com}, which no sign-in here would ever clear, the single-page app honours only the
 * {@code __Host-} name when its page is served over https (it reads both over plain http, where the
 * {@code local} profile writes the plain name). The marker is a user-experience guard, not an access control.
 */
public final class SignedOutMarker {

    /** The name over https (the {@code __Host-} prefix requires {@code Secure}, path {@code /}, no {@code Domain}). */
    public static final String SECURE_NAME = "__Host-ep_signed_out";
    /** The name over plain http (the {@code local} profile), where the prefix is not allowed. */
    public static final String PLAIN_NAME = "ep_signed_out";

    private final boolean secure;

    public SignedOutMarker(boolean secureCookies) {
        this.secure = secureCookies;
    }

    public String cookieName() {
        return secure ? SECURE_NAME : PLAIN_NAME;
    }

    /** Runs when {@code POST /api/auth/logout} is accepted (CSRF checked), before the 204 is written. */
    public LogoutHandler onLogout() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) ->
                set(response);
    }

    /** Leaves the marker: a session cookie, readable by the single-page app. */
    public void set(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, builder("1").build().toString());
    }

    /** Removes the marker (a successful sign-in). */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, builder("").maxAge(Duration.ZERO).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(cookieName(), value)
                .path("/")
                .secure(secure)
                .httpOnly(false)
                .sameSite("Lax");
    }
}
