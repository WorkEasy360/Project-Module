package com.projectmodule.security.web;

import com.projectmodule.security.Workeasy360Principal;
import com.projectmodule.security.Workeasy360Properties;
import com.projectmodule.security.oidc.Workeasy360Provider;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two reads of the sign-in API that are not the OAuth redirects themselves. (Sign-in start and callback
 * are Spring Security filters; {@code POST /api/auth/logout} is its logout filter, CSRF-protected.)
 *
 * <p>These controllers live outside the {@code /api/v1} prefix that {@code ApiConfiguration} gives every
 * other controller.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final Workeasy360Properties properties;
    private final Workeasy360Provider provider;

    public AuthController(Workeasy360Properties properties, Workeasy360Provider provider) {
        this.properties = properties;
        this.provider = provider;
    }

    /**
     * Who is signed in. Requires a session (anything else is the 401 problem from the filter chain), so
     * the single-page app learns "signed out" from a 401 and never from an error body.
     */
    @GetMapping("/session")
    public Map<String, Object> session(Authentication authentication) {
        Workeasy360Principal principal = (Workeasy360Principal) authentication.getPrincipal();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", principal.userId());
        user.put("email", principal.email());
        user.put("name", principal.name());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authenticated", true);
        response.put("user", user);
        response.put("organization", Map.of("id", principal.organizationId()));
        response.put("workeasy360SignOut", properties.signOutOfWorkeasy360Offered());
        return response;
    }

    /**
     * "Sign out of Easy Project and Workeasy360": sends the browser to Workeasy360's
     * {@code end_session_endpoint} with this client's id and its registered post-logout URI (Workeasy360
     * asks the person to confirm). Changes nothing here, so a link to it cannot sign anyone out of Easy
     * Project; the single-page app ends the local session first. Without a post-logout URI configured, or
     * when Workeasy360 advertises no end-session endpoint, it lands on the home page.
     */
    @GetMapping("/workeasy360/logout")
    public ResponseEntity<Void> endSession() {
        String target = "/";
        if (properties.signOutOfWorkeasy360Offered()) {
            try {
                String endpoint = provider.metadataString("end_session_endpoint");
                if (endpoint != null) {
                    target = endpoint + (endpoint.contains("?") ? "&" : "?")
                            + "client_id=" + URLEncoder.encode(properties.clientId(), StandardCharsets.UTF_8)
                            + "&post_logout_redirect_uri="
                            + URLEncoder.encode(properties.postLogoutRedirectUri(), StandardCharsets.UTF_8);
                }
            } catch (OAuth2AuthenticationException unavailable) {
                log.warn("workeasy360.end_session_unavailable");
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(target).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
