package com.projectmodule.work.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AIRecommendationTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId REQUESTER = ExternalUserId.of(UUID.randomUUID());

    private static AIRecommendation sample() {
        return AIRecommendation.create(PROJECT_ID, RecommendationType.PROJECT_SUMMARY, null, null,
                "Summary title", "Because the project has active risks", "{\"foo\":\"bar\"}", REQUESTER);
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("creates a PENDING recommendation with the supplied fields")
        void createsPendingRecommendation() {
            AIRecommendation recommendation = sample();

            assertThat(recommendation.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(recommendation.getType()).isEqualTo(RecommendationType.PROJECT_SUMMARY);
            assertThat(recommendation.getResourceId()).isEmpty();
            assertThat(recommendation.getQuestion()).isEmpty();
            assertThat(recommendation.getTitle()).isEqualTo("Summary title");
            assertThat(recommendation.getRationale()).isEqualTo("Because the project has active risks");
            assertThat(recommendation.getPayload()).contains("{\"foo\":\"bar\"}");
            assertThat(recommendation.getStatus()).isEqualTo(RecommendationStatus.PENDING);
            assertThat(recommendation.getRequestedBy()).isEqualTo(REQUESTER);
            assertThat(recommendation.getRespondedBy()).isEmpty();
            assertThat(recommendation.getRespondedAt()).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank title")
        void rejectsBlankTitle() {
            assertThatThrownBy(() -> AIRecommendation.create(PROJECT_ID, RecommendationType.RISK_ANALYSIS,
                    null, null, "   ", "rationale", null, REQUESTER))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejects a blank rationale")
        void rejectsBlankRationale() {
            assertThatThrownBy(() -> AIRecommendation.create(PROJECT_ID, RecommendationType.RISK_ANALYSIS,
                    null, null, "title", "   ", null, REQUESTER))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("allows a null payload, for a pure-narrative recommendation")
        void allowsNullPayload() {
            AIRecommendation recommendation = AIRecommendation.create(PROJECT_ID, RecommendationType.WHAT_IF,
                    null, "What if we add two engineers?", "title", "rationale", null, REQUESTER);

            assertThat(recommendation.getPayload()).isEmpty();
            assertThat(recommendation.getQuestion()).contains("What if we add two engineers?");
        }
    }

    @Nested
    class Accepting {

        @Test
        @DisplayName("accept() moves PENDING to ACCEPTED and records the responder")
        void acceptMovesToAccepted() {
            AIRecommendation recommendation = sample();
            ExternalUserId responder = ExternalUserId.of(UUID.randomUUID());

            recommendation.accept(responder);

            assertThat(recommendation.getStatus()).isEqualTo(RecommendationStatus.ACCEPTED);
            assertThat(recommendation.getRespondedBy()).contains(responder);
            assertThat(recommendation.getRespondedAt()).isPresent();
        }

        @Test
        @DisplayName("accept() rejects an already-accepted recommendation")
        void acceptRejectsAlreadyAccepted() {
            AIRecommendation recommendation = sample();
            recommendation.accept(ExternalUserId.of(UUID.randomUUID()));

            assertThatThrownBy(() -> recommendation.accept(ExternalUserId.of(UUID.randomUUID())))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("accept() rejects an already-rejected recommendation")
        void acceptRejectsAlreadyRejected() {
            AIRecommendation recommendation = sample();
            recommendation.reject(ExternalUserId.of(UUID.randomUUID()));

            assertThatThrownBy(() -> recommendation.accept(ExternalUserId.of(UUID.randomUUID())))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    class Rejecting {

        @Test
        @DisplayName("reject() moves PENDING to REJECTED and records the responder")
        void rejectMovesToRejected() {
            AIRecommendation recommendation = sample();
            ExternalUserId responder = ExternalUserId.of(UUID.randomUUID());

            recommendation.reject(responder);

            assertThat(recommendation.getStatus()).isEqualTo(RecommendationStatus.REJECTED);
            assertThat(recommendation.getRespondedBy()).contains(responder);
            assertThat(recommendation.getRespondedAt()).isPresent();
        }

        @Test
        @DisplayName("reject() rejects an already-rejected recommendation")
        void rejectRejectsAlreadyRejected() {
            AIRecommendation recommendation = sample();
            recommendation.reject(ExternalUserId.of(UUID.randomUUID()));

            assertThatThrownBy(() -> recommendation.reject(ExternalUserId.of(UUID.randomUUID())))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
