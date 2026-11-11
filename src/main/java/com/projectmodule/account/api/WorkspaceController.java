package com.projectmodule.account.api;

import com.projectmodule.account.api.dto.AccountResponse;
import com.projectmodule.account.api.dto.InvitationResponse;
import com.projectmodule.account.api.dto.InviteRequest;
import com.projectmodule.account.application.CreatedInvitation;
import com.projectmodule.account.application.PasswordApplicationService;
import com.projectmodule.account.application.WorkspaceApplicationService;
import com.projectmodule.common.context.RequestContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The people in the caller's workspace, and invitations to join it. */
@RestController
@RequestMapping("/workspace")
public class WorkspaceController {

    private final WorkspaceApplicationService workspaceApplicationService;
    private final PasswordApplicationService passwordApplicationService;
    private final RequestContext requestContext;

    public WorkspaceController(WorkspaceApplicationService workspaceApplicationService,
                               PasswordApplicationService passwordApplicationService,
                               RequestContext requestContext) {
        this.passwordApplicationService = passwordApplicationService;
        this.workspaceApplicationService = workspaceApplicationService;
        this.requestContext = requestContext;
    }

    @GetMapping("/people")
    public List<AccountResponse> people() {
        return workspaceApplicationService.listPeople(requestContext).stream().map(AccountResponse::from).toList();
    }

    /** A one-time link an admin can pass to a teammate who has forgotten their password. */
    @PostMapping("/people/{id}/reset-link")
    public Map<String, String> resetLink(@PathVariable UUID id) {
        return Map.of("token", passwordApplicationService.createResetTokenForTeammate(requestContext, id));
    }

    @GetMapping("/invitations")
    public List<InvitationResponse> invitations() {
        return workspaceApplicationService.listPendingInvitations(requestContext).stream()
                .map(InvitationResponse::from).toList();
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@Valid @RequestBody InviteRequest request) {
        CreatedInvitation created = workspaceApplicationService.invite(requestContext, request.email());
        return InvitationResponse.from(created.invitation(), created.token());
    }

    @DeleteMapping("/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        workspaceApplicationService.revokeInvitation(requestContext, id);
    }
}
