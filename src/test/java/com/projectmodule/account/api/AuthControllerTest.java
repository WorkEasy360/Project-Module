package com.projectmodule.account.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.account.application.AccountApplicationService;
import com.projectmodule.account.application.SignedInAccount;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.config.ApiConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(ApiConfiguration.class)
class AuthControllerTest {

    private static final UserAccount ACCOUNT = UserAccount.register("ada@example.com", "Ada", "$2a$hash");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountApplicationService accountApplicationService;

    @MockitoBean
    private com.projectmodule.account.application.WorkspaceApplicationService workspaceApplicationService;

    @MockitoBean
    private com.projectmodule.account.application.PasswordApplicationService passwordApplicationService;

    @MockitoBean
    private com.projectmodule.account.application.GoogleSignInService googleSignInService;

    @MockitoBean
    private com.projectmodule.integration.port.EmailPort emailPort;

    @MockitoBean
    private RequestContext requestContext;

    @MockitoBean
    private com.projectmodule.account.application.LegacyHandoverService legacyHandoverService;

    @BeforeEach
    void setUp() {
        when(requestContext.correlationId()).thenReturn("corr-test");
    }

    private static SignedInAccount signedIn() {
        return new SignedInAccount(ACCOUNT, "the-token", Instant.now().plusSeconds(60));
    }

    @Test
    @DisplayName("register returns 201 with a session, and never the password hash")
    void registers() throws Exception {
        when(accountApplicationService.register("ada@example.com", "Ada", "correct horse", true))
                .thenReturn(signedIn());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ada","email":"ada@example.com","password":"correct horse","rememberMe":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("the-token"))
                .andExpect(jsonPath("$.account.email").value("ada@example.com"))
                .andExpect(jsonPath("$.account.name").value("Ada"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("hash"))));
    }

    @Test
    @DisplayName("an earlier sign-in moves onto email and password with its old ids, and gets a session")
    void movesEarlierSignIn() throws Exception {
        UUID user = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(legacyHandoverService.claim(user, org, "ada@example.com", "Ada", "correct horse", true)).thenReturn(signedIn());

        mockMvc.perform(post("/api/v1/auth/legacy/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user + "\",\"organizationId\":\"" + org
                                + "\",\"name\":\"Ada\",\"email\":\"ada@example.com\",\"password\":\"correct horse\",\"rememberMe\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("the-token"));

        mockMvc.perform(post("/api/v1/auth/legacy/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"not-an-id\",\"organizationId\":\"" + org
                                + "\",\"name\":\"Ada\",\"email\":\"ada@example.com\",\"password\":\"correct horse\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("register rejects a malformed email before any account work happens")
    void rejectsMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ada","email":"not-an-email","password":"correct horse"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        verify(accountApplicationService, never()).register(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("login returns a session")
    void logsIn() throws Exception {
        when(accountApplicationService.login("ada@example.com", "correct horse", false)).thenReturn(signedIn());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"correct horse"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("the-token"));
    }

    @Test
    @DisplayName("a refused login is a 401 carrying the message the sign-in form shows")
    void refusedLoginIs401() throws Exception {
        when(accountApplicationService.login("ada@example.com", "wrong password", false))
                .thenThrow(new MissingIdentityException("Incorrect email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"wrong password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Incorrect email or password"));
    }

    @Test
    @DisplayName("logout revokes the presented token")
    void logsOut() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer the-token"))
                .andExpect(status().isNoContent());

        verify(accountApplicationService).logout("the-token");
    }

    @Test
    @DisplayName("logout with no token is still a success: the caller is signed out either way")
    void logoutWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());

        verify(accountApplicationService, never()).logout(anyString());
    }

    @Test
    @DisplayName("me returns the signed-in account")
    void returnsCurrentAccount() throws Exception {
        UUID id = ACCOUNT.getId();
        when(requestContext.requireUserId()).thenReturn(ExternalUserId.of(id));
        when(accountApplicationService.getAccount(id)).thenReturn(ACCOUNT);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    @DisplayName("me without a session is a 401")
    void meRequiresSession() throws Exception {
        when(requestContext.requireUserId()).thenThrow(new MissingIdentityException("none"));

        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }
}
