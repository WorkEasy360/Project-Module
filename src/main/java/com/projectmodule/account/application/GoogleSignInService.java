package com.projectmodule.account.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.exception.IntegrationUnavailableException;
import com.projectmodule.common.exception.MissingIdentityException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * "Sign in with Google", available only when {@code projectmodule.auth.google-client-id} is set.
 *
 * <p>Google's own token endpoint checks the credential's signature and expiry. This checks it was
 * issued for this application and that Google has verified the address. A person new to the app
 * gets a workspace of their own and a random password they can replace with "Forgot password".
 */
@Service
public class GoogleSignInService {

    private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenInfo(String aud, String email, @JsonProperty("email_verified") String emailVerified, String name) {
    }

    private final String clientId;
    private final UserAccountRepository accountRepository;
    private final AccountApplicationService accountApplicationService;
    private final PasswordEncoder passwordEncoder;
    private final RestClient restClient;

    public GoogleSignInService(@Value("${projectmodule.auth.google-client-id:}") String clientId,
                               UserAccountRepository accountRepository,
                               AccountApplicationService accountApplicationService,
                               PasswordEncoder passwordEncoder,
                               RestClient.Builder restClientBuilder) {
        this.clientId = clientId;
        this.accountRepository = accountRepository;
        this.accountApplicationService = accountApplicationService;
        this.passwordEncoder = passwordEncoder;
        this.restClient = restClientBuilder.build();
    }

    public String clientId() {
        return clientId;
    }

    @Transactional
    public SignedInAccount signIn(String credential, boolean rememberMe) {
        if (clientId.isBlank()) {
            throw new IntegrationUnavailableException("Google sign-in", null);
        }
        return signInVerified(verify(credential), rememberMe);
    }

    SignedInAccount signInVerified(TokenInfo info, boolean rememberMe) {
        if (!clientId.equals(info.aud()) || !"true".equals(info.emailVerified()) || info.email() == null) {
            throw new MissingIdentityException("Google could not confirm this account");
        }
        String email = UserAccount.normalizeEmail(info.email());
        return accountRepository.findByEmail(email)
                .map(account -> accountApplicationService.signInWithoutPassword(account, rememberMe))
                .orElseGet(() -> accountApplicationService.saveAndSignIn(
                        UserAccount.register(email, displayName(info, email),
                                passwordEncoder.encode(UUID.randomUUID().toString())),
                        rememberMe));
    }

    private TokenInfo verify(String credential) {
        try {
            return restClient.get()
                    .uri(TOKEN_INFO_URL + "?id_token={credential}", credential)
                    .retrieve()
                    .body(TokenInfo.class);
        } catch (RestClientResponseException e) {
            throw new MissingIdentityException("Google could not confirm this account");
        } catch (RestClientException e) {
            throw new IntegrationUnavailableException("Google sign-in", e);
        }
    }

    private static String displayName(TokenInfo info, String email) {
        return info.name() == null || info.name().isBlank() ? email.substring(0, email.indexOf('@')) : info.name();
    }
}
