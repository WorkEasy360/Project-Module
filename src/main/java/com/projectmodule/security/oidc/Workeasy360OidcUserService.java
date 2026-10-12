package com.projectmodule.security.oidc;

import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.security.SignInErrors;
import com.projectmodule.security.Workeasy360Principal;
import com.projectmodule.security.identity.FederatedIdentityStore;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The last step of authentication, run by Spring's OIDC provider after the token has been exchanged and
 * the ID token verified (signature, issuer, audience, time, nonce, type, product). It:
 *
 * <ol>
 *   <li>does <strong>not</strong> call the userinfo endpoint: the ID token already carries everything;
 *   <li>binds the sign-in to the organization the launcher named: if the start asked for organization A
 *       and the token says B, the sign-in is refused ({@code wrong_organization});
 *   <li>maps (issuer, sub) to the internal user id (creating the mapping on a first sign-in); the
 *       organization is the token's {@code organization_id}.
 * </ol>
 *
 * Because this runs inside the authentication step, a refusal here means no session exists afterwards.
 */
public class Workeasy360OidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final Logger log = LoggerFactory.getLogger(Workeasy360OidcUserService.class);

    private final FederatedIdentityStore identities;

    public Workeasy360OidcUserService(FederatedIdentityStore identities) {
        this.identities = identities;
    }

    /** What a successful sign-in produced: the verified token plus the principal to keep in the session. */
    public static final class SignedInUser extends DefaultOidcUser {

        private static final long serialVersionUID = 1L;

        private final Workeasy360Principal principal;
        private final String next;

        SignedInUser(OidcIdToken idToken, Workeasy360Principal principal, String next) {
            super(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, null, "sub");
            this.principal = principal;
            this.next = next;
        }

        public Workeasy360Principal principal() {
            return principal;
        }

        /** The sanitized route to land on, from the start request. */
        public String next() {
            return next;
        }
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcIdToken token = userRequest.getIdToken();
        OAuth2AuthorizationRequest started = startedRequest();
        if (started == null) {
            throw failure("authorization_request_not_found");
        }
        UUID tokenOrganization = IdTokenDecoderFactory.isUuid(token.getClaimAsString("organization_id"))
                ? UUID.fromString(token.getClaimAsString("organization_id")) : null;
        if (tokenOrganization == null) {
            throw failure(IdTokenDecoderFactory.INVALID_ID_TOKEN);
        }
        String requested = started.getAttribute(AuthorizationRequestResolver.ATTR_ORGANIZATION);
        if (requested != null && !requested.isEmpty() && !UUID.fromString(requested).equals(tokenOrganization)) {
            // The launcher asked for one organization and the provider answered for another.
            log.warn("workeasy360.sign_in_failed code={}", SignInErrors.WRONG_ORGANIZATION);
            throw failure(SignInErrors.WRONG_ORGANIZATION);
        }
        String issuer = userRequest.getClientRegistration().getProviderDetails().getIssuerUri();
        ExternalUserId userId;
        try {
            userId = identities.resolve(issuer, token.getSubject(), token.getEmail(), token.getFullName());
        } catch (RuntimeException exception) {
            log.error("workeasy360.identity_store_failed type={}", exception.getClass().getSimpleName());
            throw failure(Workeasy360Provider.UNAVAILABLE);
        }
        Workeasy360Principal principal = new Workeasy360Principal(
                userId.value(), tokenOrganization, issuer, token.getSubject(),
                clip(token.getEmail(), 320), clip(token.getFullName(), 200), aal(token), authTime(token));
        String next = started.getAttribute(AuthorizationRequestResolver.ATTR_NEXT);
        return new SignedInUser(token, principal, next == null ? NextPath.HOME : next);
    }

    private static OAuth2AuthorizationRequest startedRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            Object consumed = servlet.getRequest().getAttribute(PendingFlowRepository.CONSUMED_ATTRIBUTE);
            return consumed instanceof OAuth2AuthorizationRequest request ? request : null;
        }
        return null;
    }

    private static int aal(OidcIdToken token) {
        Object value = token.getClaim("aal");
        return value instanceof Number number ? number.intValue() : 1;
    }

    private static Instant authTime(OidcIdToken token) {
        return token.getAuthenticatedAt();
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static OAuth2AuthenticationException failure(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }
}
