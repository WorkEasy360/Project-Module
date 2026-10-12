package com.projectmodule.security.oidc;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where to land after a successful sign-in: a same-site route, or the home page.
 *
 * <p>The single-page app uses hash routes, so {@code next} is the route written as a path
 * ({@code /projects/123}); the success handler turns it into {@code /#/projects/123}. The value is
 * still validated as a same-site path first (universal-SSO design section 3.8, ported from Easy CRM's
 * {@code _safe_next} and EasyBook's {@code sameSitePath}): it starts with a single {@code /}, has no
 * backslash, no whitespace or control character (browsers drop tabs and newlines inside URLs, which
 * would turn {@code /<tab>/evil} into {@code //evil}), is at most 2,048 characters, and is not an
 * authentication page. Percent-encoded slashes, backslashes and control characters are refused too.
 * Anything else lands on the home page.
 */
public final class NextPath {

    public static final String HOME = "/";
    static final int MAX_LENGTH = 2048;

    private static final Pattern ENCODED_DANGER =
            Pattern.compile("%(2f|5c|[01][0-9a-f]|7f)", Pattern.CASE_INSENSITIVE);

    private NextPath() {
    }

    /** The validated route (always starting with {@code /}), or {@link #HOME}. */
    public static String sanitize(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return HOME;
        }
        String route = value;
        if (route.startsWith("/#/")) {
            // A caller that already wrote the hash form: keep the route.
            route = route.substring(2);
        }
        if (!route.startsWith("/") || route.startsWith("//") || route.startsWith("/\\")) {
            return HOME;
        }
        for (int i = 0; i < route.length(); i++) {
            char c = route.charAt(i);
            if (c == '\\' || c == '#' || Character.isISOControl(c) || Character.isWhitespace(c)
                    || Character.isSpaceChar(c)) {
                return HOME;
            }
        }
        if (ENCODED_DANGER.matcher(route).find()) {
            return HOME;
        }
        String path = route;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.equals("/api") || lower.startsWith("/api/") || lower.equals("/sign-in")
                || lower.startsWith("/sign-in/")) {
            return HOME;
        }
        return route;
    }

    /** The URL to redirect the browser to for a sanitized route. */
    public static String landingUrl(String sanitizedRoute) {
        return HOME.equals(sanitizedRoute) ? HOME : "/#" + sanitizedRoute;
    }
}
