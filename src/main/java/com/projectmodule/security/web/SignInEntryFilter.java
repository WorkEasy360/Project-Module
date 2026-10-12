package com.projectmodule.security.web;

import com.projectmodule.security.SignInErrors;
import com.projectmodule.security.oidc.AuthorizationRequestResolver;
import com.projectmodule.security.oidc.IdTokenDecoderFactory;
import com.projectmodule.security.oidc.Workeasy360Provider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Two entry points in front of Spring's authorization-request redirect:
 *
 * <ul>
 *   <li><strong>The launcher.</strong> {@code GET /?organization=<uuid>} always starts a sign-in for that
 *       organization, even when a session exists (the person picked that organization in the Workeasy360
 *       launcher). A value that is not a UUID is ignored and the app is served as usual. A plain {@code /}
 *       never starts anything: the sign-in page does not auto-start Workeasy360 either.
 *   <li><strong>Start pre-flight.</strong> Spring would answer a failed discovery with a bare 500. The
 *       pre-flight asks for the registration first, so a Workeasy360 that is down (or a product that is not
 *       configured) becomes the sign-in error {@code unavailable} on the sign-in page.
 * </ul>
 */
public class SignInEntryFilter extends OncePerRequestFilter {

    private final Workeasy360Provider provider;

    public SignInEntryFilter(Workeasy360Provider provider) {
        this.provider = provider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("GET".equals(request.getMethod())) {
            String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
            if ("/".equals(path)) {
                String organization = request.getParameter("organization");
                if (IdTokenDecoderFactory.isUuid(organization == null ? null : organization.trim())) {
                    redirect(response, AuthorizationRequestResolver.START_PATH + "?organization="
                            + URLEncoder.encode(organization.trim().toLowerCase(Locale.ROOT), StandardCharsets.UTF_8));
                    return;
                }
            } else if (AuthorizationRequestResolver.START_PATH.equals(path)) {
                try {
                    provider.registration();
                } catch (OAuth2AuthenticationException unavailable) {
                    redirect(response, "/#/sign-in?sso_error=" + SignInErrors.UNAVAILABLE);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    static void redirect(HttpServletResponse response, String location) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", location);
        response.setHeader("Cache-Control", "no-store");
    }
}
