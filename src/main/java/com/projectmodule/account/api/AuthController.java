package com.projectmodule.account.api;

import com.projectmodule.account.api.dto.AcceptInvitationRequest;
import com.projectmodule.account.api.dto.AccountResponse;
import com.projectmodule.account.api.dto.InvitationResponse;
import com.projectmodule.account.api.dto.LoginRequest;
import com.projectmodule.account.api.dto.PreviewInvitationRequest;
import com.projectmodule.account.api.dto.RegisterRequest;
import com.projectmodule.account.api.dto.SessionResponse;
import com.projectmodule.account.application.AccountApplicationService;
import com.projectmodule.account.application.GoogleSignInService;
import com.projectmodule.account.application.LegacyHandoverService;
import com.projectmodule.account.application.PasswordApplicationService;
import com.projectmodule.account.application.WorkspaceApplicationService;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.config.RequestContextFilter;
import com.projectmodule.integration.port.EmailPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Create an account, sign in, sign out, and ask who is signed in. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    public record GoogleSignInRequest(@NotBlank String credential, boolean rememberMe) {
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
    }

    /** The ids an earlier sign-in kept in the browser, and the email and password to use from now on. */
    public record LegacyClaimRequest(@NotNull UUID userId, @NotNull UUID organizationId,
                                     @NotBlank @Size(max = 120) String name,
                                     @NotBlank @Email @Size(max = 254) String email,
                                     @NotBlank String password, boolean rememberMe) {
    }


    private final AccountApplicationService accountApplicationService;
    private final WorkspaceApplicationService workspaceApplicationService;
    private final PasswordApplicationService passwordApplicationService;
    private final GoogleSignInService googleSignInService;
    private final EmailPort emailPort;
    private final RequestContext requestContext;
    private final LegacyHandoverService legacyHandoverService;

    public AuthController(AccountApplicationService accountApplicationService,
                          WorkspaceApplicationService workspaceApplicationService,
                          PasswordApplicationService passwordApplicationService,
                          GoogleSignInService googleSignInService,
                          EmailPort emailPort,
                          RequestContext requestContext,
                          LegacyHandoverService legacyHandoverService) {
        this.legacyHandoverService = legacyHandoverService;
        this.passwordApplicationService = passwordApplicationService;
        this.googleSignInService = googleSignInService;
        this.emailPort = emailPort;
        this.accountApplicationService = accountApplicationService;
        this.workspaceApplicationService = workspaceApplicationService;
        this.requestContext = requestContext;
    }

    /** What the sign-in page should offer. Public: it holds no secret. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("googleClientId", googleSignInService.clientId(), "emailEnabled", emailPort.isEnabled(),
                "legacyHandover", legacyHandoverService.isEnabled());
    }

    /** Moves an earlier sign-in onto an email and password, keeping all of its work. */
    @PostMapping("/legacy/claim")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse claimLegacy(@Valid @RequestBody LegacyClaimRequest request) {
        return SessionResponse.from(legacyHandoverService.claim(request.userId(), request.organizationId(),
                request.email(), request.name(), request.password(), request.rememberMe()));
    }

    @PostMapping("/google")
    public SessionResponse google(@Valid @RequestBody GoogleSignInRequest request) {
        return SessionResponse.from(googleSignInService.signIn(request.credential(), request.rememberMe()));
    }

    @PatchMapping("/me")
    public AccountResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return AccountResponse.from(accountApplicationService.rename(
                requestContext.requireUserId().value(), request.name()));
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordApplicationService.changePassword(requestContext, request.currentPassword(), request.newPassword());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordApplicationService.requestReset(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordApplicationService.resetPassword(request.token(), request.newPassword());
    }

    @PostMapping("/invitation/preview")
    public InvitationResponse previewInvitation(@Valid @RequestBody PreviewInvitationRequest request) {
        return InvitationResponse.from(workspaceApplicationService.previewInvitation(request.token()));
    }

    @PostMapping("/invitation/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        return SessionResponse.from(workspaceApplicationService.acceptInvitation(
                request.token(), request.name(), request.password(), request.rememberMe()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse register(@Valid @RequestBody RegisterRequest request) {
        return SessionResponse.from(accountApplicationService.register(
                request.email(), request.name(), request.password(), request.rememberMe()));
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request) {
        return SessionResponse.from(accountApplicationService.login(
                request.email(), request.password(), request.rememberMe()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = RequestContextFilter.bearerToken(authorization);
        if (token != null) {
            accountApplicationService.logout(token);
        }
    }

    @GetMapping("/me")
    public AccountResponse me() {
        return AccountResponse.from(accountApplicationService.getAccount(requestContext.requireUserId().value()));
    }
}
