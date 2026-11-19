package com.projectmodule.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.common.context.AuthenticatedIdentity;
import com.projectmodule.common.context.BearerTokenAuthenticator;
import com.projectmodule.common.context.RequestContextHeaders;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.security.AccountTokenAuthentication;
import com.projectmodule.security.ProblemResponses;
import com.projectmodule.security.TrustedGatewayAuthentication;
import com.projectmodule.security.Workeasy360Authentication;
import com.projectmodule.security.Workeasy360Principal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Email sign-in's bearer tokens and the trusted-gateway mode, on the business API. These are the cases main's
 * former {@code RequestContextFilterTest} pinned, now against the filter in the security chain that replaced it
 * (the correlation id it also echoed is {@code CorrelationIdFilterTest}'s).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Email sign-in: the bearer token (and the trusted-gateway mode) on /api/v1/**")
class AccountTokenFilterTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static final String ORG = "22222222-2222-2222-2222-222222222222";

    @Mock
    private BearerTokenAuthenticator authenticator;

    /** What the rest of the chain saw. */
    private final AtomicReference<Authentication> seen = new AtomicReference<>();
    private boolean chainCalled;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private AccountTokenFilter filter(boolean trustIdentityHeaders) {
        return new AccountTokenFilter(() -> authenticator, trustIdentityHeaders,
                new ProblemResponses(new ObjectMapper()));
    }

    private MockHttpServletResponse run(AccountTokenFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            chainCalled = true;
            seen.set(SecurityContextHolder.getContext().getAuthentication());
        });
        return response;
    }

    private static MockHttpServletRequest api(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/projects");
        request.setServletPath("/api/v1/projects");
        return request;
    }

    @Test
    @DisplayName("a valid session token establishes the caller, for this request only")
    void validTokenEstablishesCaller() throws Exception {
        ExternalUserId user = ExternalUserId.of(UUID.randomUUID());
        OrganizationId org = OrganizationId.of(UUID.randomUUID());
        when(authenticator.authenticate("good")).thenReturn(Optional.of(new AuthenticatedIdentity(user, org)));
        MockHttpServletRequest request = api("GET");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good");

        run(filter(false), request);

        assertThat(chainCalled).isTrue();
        assertThat(seen.get()).isInstanceOf(AccountTokenAuthentication.class);
        AuthenticatedIdentity identity = ((AccountTokenAuthentication) seen.get()).getPrincipal();
        assertThat(identity.userId()).isEqualTo(user);
        assertThat(identity.organizationId()).isEqualTo(org);
        assertThat(request.getSession(false)).as("nothing is kept in a session").isNull();
    }

    @Test
    @DisplayName("identity headers are ignored by default, so nobody can claim to be someone else")
    void ignoresIdentityHeadersByDefault() throws Exception {
        MockHttpServletRequest request = api("GET");
        request.addHeader(RequestContextHeaders.USER_ID, USER);
        request.addHeader(RequestContextHeaders.ORGANIZATION_ID, ORG);

        run(filter(false), request);

        assertThat(chainCalled).isTrue();
        assertThat(seen.get()).isNull();
        assertThat(AccountTokenFilter.carriesExplicitCredential(request, false)).isFalse();
    }

    @Test
    @DisplayName("an invalid token means anonymous, even alongside identity headers a gateway could set")
    void invalidTokenNeverFallsBackToHeaders() throws Exception {
        when(authenticator.authenticate("dead")).thenReturn(Optional.empty());
        MockHttpServletRequest request = api("GET");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer dead");
        request.addHeader(RequestContextHeaders.USER_ID, USER);
        request.addHeader(RequestContextHeaders.ORGANIZATION_ID, ORG);

        run(filter(true), request);

        assertThat(chainCalled).isTrue();
        assertThat(seen.get()).isNull();
    }

    @Test
    @DisplayName("a bearer token, valid or not, decides the request: a Workeasy360 session sent with it is not used")
    void bearerTokenWinsOverTheSession() throws Exception {
        Workeasy360Principal someoneElse = new Workeasy360Principal(UUID.randomUUID(), UUID.randomUUID(),
                "https://accounts.workeasy360.com", "sub", null, null, 1, Instant.now());
        SecurityContext loadedFromSession = SecurityContextHolder.createEmptyContext();
        loadedFromSession.setAuthentication(new Workeasy360Authentication(someoneElse));
        SecurityContextHolder.setContext(loadedFromSession);
        when(authenticator.authenticate("dead")).thenReturn(Optional.empty());
        MockHttpServletRequest request = api("POST");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer dead");

        run(filter(false), request);

        assertThat(seen.get()).as("anonymous: a dead token never borrows the session's identity").isNull();
        assertThat(loadedFromSession.getAuthentication()).as("the session's own context is left as it was")
                .isInstanceOf(Workeasy360Authentication.class);
        assertThat(AccountTokenFilter.carriesExplicitCredential(request, false))
                .as("so CSRF and the same-origin check, which protect the cookie, do not apply").isTrue();
    }

    @Test
    @DisplayName("identity headers are honoured when a deployment says a gateway sets them")
    void trustsHeadersWhenConfigured() throws Exception {
        MockHttpServletRequest request = api("GET");
        request.addHeader(RequestContextHeaders.USER_ID, USER);

        run(filter(true), request);

        assertThat(chainCalled).isTrue();
        assertThat(seen.get()).isInstanceOf(TrustedGatewayAuthentication.class);
        assertThat(((TrustedGatewayAuthentication) seen.get()).userId()).isEqualTo(UUID.fromString(USER));
        assertThat(((TrustedGatewayAuthentication) seen.get()).organizationId()).isNull();
        assertThat(AccountTokenFilter.carriesExplicitCredential(request, true)).isTrue();
        verifyNoInteractions(authenticator);
    }

    @Test
    @DisplayName("passes a request with no identity through untouched")
    void allowsAnonymousRequest() throws Exception {
        MockHttpServletResponse response = run(filter(true), api("GET"));

        assertThat(chainCalled).isTrue();
        assertThat(seen.get()).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("answers a malformed trusted header itself, since no advice runs this early")
    void rejectsMalformedHeader() throws Exception {
        MockHttpServletRequest request = api("GET");
        request.addHeader(RequestContextHeaders.USER_ID, "not-a-uuid");

        MockHttpServletResponse response = run(filter(true), request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("validation-failed");
        assertThat(chainCalled).isFalse();
    }

    @Test
    @DisplayName("trusted headers: surrounding whitespace is trimmed, and both ids are read")
    void trimsTrustedHeaders() throws Exception {
        MockHttpServletRequest request = api("GET");
        request.addHeader(RequestContextHeaders.USER_ID, "  " + USER + " ");
        request.addHeader(RequestContextHeaders.ORGANIZATION_ID, " " + ORG + "  ");

        run(filter(true), request);

        TrustedGatewayAuthentication gateway = (TrustedGatewayAuthentication) seen.get();
        assertThat(gateway.userId()).isEqualTo(UUID.fromString(USER));
        assertThat(gateway.organizationId()).isEqualTo(UUID.fromString(ORG));
    }

    @Test
    @DisplayName("trusted headers: a blank header is absent, so blank headers alone identify nobody")
    void blankTrustedHeadersAreAbsent() throws Exception {
        MockHttpServletRequest request = api("GET");
        request.addHeader(RequestContextHeaders.USER_ID, "   ");
        request.addHeader(RequestContextHeaders.ORGANIZATION_ID, "");

        run(filter(true), request);

        assertThat(chainCalled).isTrue();
        assertThat(seen.get()).isNull();
        assertThat(AccountTokenFilter.carriesExplicitCredential(request, true)).isFalse();
    }

    @Test
    @DisplayName("trusted headers: a malformed organization id is answered 400 too")
    void rejectsMalformedOrganization() throws Exception {
        MockHttpServletRequest request = api("GET");
        request.addHeader(RequestContextHeaders.USER_ID, USER);
        request.addHeader(RequestContextHeaders.ORGANIZATION_ID, "not-a-uuid");

        MockHttpServletResponse response = run(filter(true), request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(chainCalled).isFalse();
    }

    @Test
    @DisplayName("only the business API: /api/auth/** (the Workeasy360 session) never reads a bearer token")
    void onlyTheBusinessApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/session");
        request.setServletPath("/api/auth/session");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good");

        run(filter(false), request);

        assertThat(chainCalled).isTrue();
        assertThat(seen.get()).isNull();
        verifyNoInteractions(authenticator);
        assertThat(AccountTokenFilter.carriesExplicitCredential(request, false)).isFalse();
    }

    @Test
    @DisplayName("with no account package in the application, every token is unknown")
    void noAuthenticator() throws Exception {
        MockHttpServletRequest request = api("GET");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer anything");
        AccountTokenFilter filter = new AccountTokenFilter(() -> null, false, new ProblemResponses(new ObjectMapper()));

        run(filter, request);

        assertThat(chainCalled).isTrue();
        assertThat(seen.get()).isNull();
    }
}
