package com.projectmodule.assistant.domain;

import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Optional;
import java.util.UUID;

/** One person's conversation with the assistant, in one workspace. */
@Entity
@Table(name = "assistant_conversations")
public class AssistantConversation extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "messages", nullable = false, columnDefinition = "text")
    private String messages;

    @Column(name = "pending_action", columnDefinition = "text")
    private String pendingAction;

    /** For JPA only. */
    protected AssistantConversation() {
    }

    private AssistantConversation(UUID id, UUID userId, UUID organizationId) {
        super(id);
        this.userId = userId;
        this.organizationId = organizationId;
        this.messages = "[]";
    }

    public static AssistantConversation start(UUID userId, UUID organizationId) {
        return new AssistantConversation(UuidV7.generate(), userId, organizationId);
    }

    public boolean belongsTo(UUID callerId, UUID callerOrganizationId) {
        return userId.equals(callerId) && organizationId.equals(callerOrganizationId);
    }

    public String getMessages() {
        return messages;
    }

    public void replaceMessages(String messagesJson) {
        this.messages = messagesJson;
    }

    public Optional<String> getPendingAction() {
        return Optional.ofNullable(pendingAction);
    }

    public void setPendingAction(String pendingActionJson) {
        this.pendingAction = pendingActionJson;
    }
}
