package com.projectmodule.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.common.context.AuthenticatedIdentity;
import com.projectmodule.common.context.BearerTokenAuthenticator;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.context.RequestContextFactory;
import com.projectmodule.common.context.RequestContextHeaders;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.UUID;
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

@ExtendWith(MockitoExtension.class)
class RequestContextFilterTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static final String ORG = "22222222-2222-2222-2222-222222222222";

    @Mock
    private FilterChain filterChain;

    @Mock
    private BearerTokenAuthenticator authenticator;

    private RequestContextFilter filter(boolean trustIdentityHeaders) {
        return new RequestContextFilter(
                new RequestContextFactory(), new ObjectMapper(), authenticator, trustIdentityHeaders);
    }

    private static RequestContext contextOf(MockHttpServletRequest request) {
        return (RequestContext) request.getAttribute(RequestContextFilter.ATTRIBUTE);
    }

    @Test
    @DisplayName("a valid session token establishes the caller")
    void validTokenEstablishesCaller() throws Exception {
        ExternalUserId user = ExternalUserId.of(UUID.randomUUID());
        OrganizationId org = OrganizationId.of(UUID.randomUUID());
        when(authenticator.authenticate("good")).thenReturn(Optional.of(new AuthenticatedIdentity(user, org)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer good");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(false).doFilter(request, response, filterChain);

        assertThat(contextOf(request).userId()).contains(user);
        assertThat(contextOf(request).organizationId()).contains(org);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("identity headers are ignored by default, so nobody can claim to be someone else")
    void ignoresIdentityHeadersByDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextHeaders.USER_ID, USER);
        request.addHeader(RequestContextHeaders.ORGANIZATION_ID, ORG);

        filter(false).doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(contextOf(request).userId()).isEmpty();
        assertThat(contextOf(request).organizationId()).isEmpty();
    }

    @Test
    @DisplayName("an invalid token means anonymous, even alongside identity headers a gateway could set")
    void invalidTokenNeverFallsBackToHeaders() throws Exception {
        when(authenticator.authenticate("dead")).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer dead");
        request.addHeader(RequestContextHeaders.USER_ID, USER);
        request.addHeader(RequestContextHeaders.ORGANIZATION_ID, ORG);

        filter(true).doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(contextOf(request).userId()).isEmpty();
    }

    @Test
    @DisplayName("identity headers are honoured when a deployment says a gateway sets them")
    void trustsHeadersWhenConfigured() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextHeaders.USER_ID, USER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(true).doFilter(request, response, filterChain);

        assertThat(contextOf(request).userId()).isPresent();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("echoes the correlation id so callers can match their logs to ours")
    void echoesCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextHeaders.CORRELATION_ID, "corr-99");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(false).doFilter(request, response, filterChain);

        assertThat(response.getHeader(RequestContextHeaders.CORRELATION_ID)).isEqualTo("corr-99");
    }

    @Test
    @DisplayName("passes a request with no identity through untouched")
    void allowsAnonymousRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(false).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("answers a malformed trusted header itself, since no advice runs this early")
    void rejectsMalformedHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestContextHeaders.USER_ID, "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(true).doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("validation-failed");
        verify(filterChain, never()).doFilter(request, response);
    }
}
