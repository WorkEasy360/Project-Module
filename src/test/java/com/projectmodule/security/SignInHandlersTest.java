package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectmodule.security.web.SignInHandlers;
import com.projectmodule.security.web.SignedOutMarker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@DisplayName("Sign-in success handler")
class SignInHandlersTest {

    @Test
    @DisplayName("an authentication that is not the one this application builds ends the session Spring just saved")
    void unexpectedAuthenticationLeavesNoSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SignInHandlers.success(new HttpSessionSecurityContextRepository(), new SignedOutMarker(true))
                .onAuthenticationSuccess(request, response, new TestingAuthenticationToken("intruder", "x"));

        assertThat(session.isInvalid()).isTrue();
        assertThat(response.getHeader("Location")).isEqualTo("/#/sign-in?sso_error=refused");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getHeaders("Set-Cookie")).as("a refused sign-in does not clear the signed-out marker").isEmpty();
    }
}
