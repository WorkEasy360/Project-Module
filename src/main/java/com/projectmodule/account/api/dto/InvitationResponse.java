package com.projectmodule.account.api.dto;

import com.projectmodule.account.domain.WorkspaceInvitation;
import java.time.Instant;
import java.util.UUID;

/** {@code token} is present only in the response that created the invitation. */
public record InvitationResponse(UUID id, String email, Instant expiresAt, String token) {

    public static InvitationResponse from(WorkspaceInvitation invitation, String token) {
        return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getExpiresAt(), token);
    }

    public static InvitationResponse from(WorkspaceInvitation invitation) {
        return from(invitation, null);
    }
}
