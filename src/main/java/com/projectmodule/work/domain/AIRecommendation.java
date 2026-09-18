package com.projectmodule.work.domain;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.UuidV7;
import com.projectmodule.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An AI-generated suggestion about a project, recorded for human review — per the approved
 * {@code docs/project/26-AI-RECOMMENDATIONS-SPEC.md}. Suggest-only: accepting one records a
 * human decision but never automatically applies anything (§1 of that specification) — applying
 * a suggestion is a separate, existing manual operation (e.g. creating a task by hand).
 *
 * <p>No soft-archive, unlike Task/Risk/Issue: a recommendation is a point-in-time suggestion,
 * not an ongoing work item. {@code REJECTED} already serves the role archiving would.
 */
@Entity
@Table(name = "ai_recommendations")
public class AIRecommendation extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private RecommendationType type;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "question", updatable = false)
    private String question;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "rationale", nullable = false)
    private String rationale;

    @Column(name = "payload")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecommendationStatus status;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "responded_by")
    private UUID respondedBy;

    @Column(name = "responded_at")
    private Instant respondedAt;

    /** For JPA only. */
    protected AIRecommendation() {
    }

    private AIRecommendation(UUID id, UUID projectId, RecommendationType type, UUID resourceId, String question,
                             String title, String rationale, String payload, ExternalUserId requestedBy) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.resourceId = resourceId;
        this.question = question;
        this.title = validateNotBlank(title, "title");
        this.rationale = validateNotBlank(rationale, "rationale");
        this.payload = payload;
        this.status = RecommendationStatus.PENDING;
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy is required").value();
    }

    /**
     * Records a newly generated recommendation. Called only after an {@code AIProviderPort} call
     * has already succeeded — there is no code path that creates a recommendation with fabricated
     * or empty content.
     *
     * @throws ValidationException if {@code title} or {@code rationale} is blank
     */
    public static AIRecommendation create(UUID projectId, RecommendationType type, UUID resourceId, String question,
                                          String title, String rationale, String payload,
                                          ExternalUserId requestedBy) {
        return new AIRecommendation(
                UuidV7.generate(), projectId, type, resourceId, question, title, rationale, payload, requestedBy);
    }

    /**
     * Records that a human accepted the suggestion. Does not apply it — see the class javadoc.
     *
     * @throws BusinessRuleViolationException if the recommendation is not {@code PENDING}
     */
    public void accept(ExternalUserId respondingUser) {
        requirePending("accept");
        this.status = RecommendationStatus.ACCEPTED;
        respondTo(respondingUser);
    }

    /**
     * Records that a human rejected the suggestion.
     *
     * @throws BusinessRuleViolationException if the recommendation is not {@code PENDING}
     */
    public void reject(ExternalUserId respondingUser) {
        requirePending("reject");
        this.status = RecommendationStatus.REJECTED;
        respondTo(respondingUser);
    }

    private void requirePending(String action) {
        if (status != RecommendationStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Cannot " + action + " a recommendation that is already " + status);
        }
    }

    private void respondTo(ExternalUserId respondingUser) {
        this.respondedBy = Objects.requireNonNull(respondingUser, "respondingUser is required").value();
        this.respondedAt = Instant.now();
    }

    private static String validateNotBlank(String candidate, String fieldName) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException(fieldName + " must not be blank");
        }
        return candidate;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public RecommendationType getType() {
        return type;
    }

    public Optional<UUID> getResourceId() {
        return Optional.ofNullable(resourceId);
    }

    public Optional<String> getQuestion() {
        return Optional.ofNullable(question);
    }

    public String getTitle() {
        return title;
    }

    public String getRationale() {
        return rationale;
    }

    public Optional<String> getPayload() {
        return Optional.ofNullable(payload);
    }

    public RecommendationStatus getStatus() {
        return status;
    }

    public ExternalUserId getRequestedBy() {
        return ExternalUserId.of(requestedBy);
    }

    public Optional<ExternalUserId> getRespondedBy() {
        return Optional.ofNullable(respondedBy).map(ExternalUserId::of);
    }

    public Optional<Instant> getRespondedAt() {
        return Optional.ofNullable(respondedAt);
    }
}
