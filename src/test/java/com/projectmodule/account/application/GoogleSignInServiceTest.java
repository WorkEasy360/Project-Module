package com.projectmodule.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.domain.WorkspaceRole;
import com.projectmodule.account.infrastructure.AuthSessionRepository;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.exception.IntegrationUnavailableException;
import com.projectmodule.common.exception.MissingIdentityException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestClient;

/** Google's own endpoint cannot be reached from a test, so this covers what is done with its answer. */
@ExtendWith(MockitoExtension.class)
class GoogleSignInServiceTest {

    private static final String CLIENT_ID = "app.apps.googleusercontent.com";

    @Mock
    private UserAccountRepository accountRepository;

    @Mock
    private AuthSessionRepository sessionRepository;

    private AccountApplicationService accounts;

    @BeforeEach
    void setUp() {
        accounts = new AccountApplicationService(accountRepository, sessionRepository, new BCryptPasswordEncoder(4));
        lenient().when(accountRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    private GoogleSignInService service(String clientId) {
        return new GoogleSignInService(clientId, accountRepository, accounts, new BCryptPasswordEncoder(4),
                RestClient.builder());
    }

    @Test
    @DisplayName("without a configured client id the feature is unavailable")
    void unavailableWithoutClientId() {
        assertThatThrownBy(() -> service("").signIn("credential", true))
                .isInstanceOf(IntegrationUnavailableException.class);
    }

    @Test
    @DisplayName("a credential issued for a different application is refused")
    void refusesAnotherApplicationsToken() {
        var info = new GoogleSignInService.TokenInfo("someone-else", "a@example.com", "true", "A");

        assertThatThrownBy(() -> service(CLIENT_ID).signInVerified(info, true))
                .isInstanceOf(MissingIdentityException.class);
    }

    @Test
    @DisplayName("an address Google has not verified is refused")
    void refusesUnverifiedEmail() {
        var info = new GoogleSignInService.TokenInfo(CLIENT_ID, "a@example.com", "false", "A");

        assertThatThrownBy(() -> service(CLIENT_ID).signInVerified(info, true))
                .isInstanceOf(MissingIdentityException.class);
    }

    @Test
    @DisplayName("an existing account signs in, keeping its workspace")
    void existingAccountSignsIn() {
        UserAccount existing = UserAccount.joinWorkspace(java.util.UUID.randomUUID(), "a@example.com", "A", "h");
        when(accountRepository.findByEmail("a@example.com")).thenReturn(Optional.of(existing));

        SignedInAccount signedIn = service(CLIENT_ID).signInVerified(
                new GoogleSignInService.TokenInfo(CLIENT_ID, "A@Example.com", "true", "A"), true);

        assertThat(signedIn.account()).isSameAs(existing);
        assertThat(signedIn.token()).isNotBlank();
    }

    @Test
    @DisplayName("someone new gets their own workspace, named as Google names them")
    void newPersonGetsAWorkspace() {
        when(accountRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        SignedInAccount signedIn = service(CLIENT_ID).signInVerified(
                new GoogleSignInService.TokenInfo(CLIENT_ID, "new@example.com", "true", "New Person"), true);

        assertThat(signedIn.account().getDisplayName()).isEqualTo("New Person");
        assertThat(signedIn.account().getWorkspaceRole()).isEqualTo(WorkspaceRole.ADMIN);
    }
}
