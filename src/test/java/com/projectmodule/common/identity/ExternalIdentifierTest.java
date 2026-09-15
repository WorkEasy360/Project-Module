package com.projectmodule.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExternalIdentifierTest {

    private static final UUID VALUE = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Nested
    class ExternalUserIdTest {

        @Test
        @DisplayName("rejects a null value")
        void rejectsNull() {
            assertThatThrownBy(() -> new ExternalUserId(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("parses a well-formed identifier")
        void parsesValid() {
            assertThat(ExternalUserId.fromString(VALUE.toString()).value()).isEqualTo(VALUE);
        }

        @Test
        @DisplayName("reports a malformed identifier as a validation failure, not a crash")
        void rejectsMalformed() {
            assertThatThrownBy(() -> ExternalUserId.fromString("not-a-uuid"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Malformed user identifier");
        }

        @Test
        @DisplayName("treats a null string as a validation failure")
        void rejectsNullString() {
            assertThatThrownBy(() -> ExternalUserId.fromString(null))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("compares by value")
        void comparesByValue() {
            assertThat(ExternalUserId.of(VALUE)).isEqualTo(ExternalUserId.of(VALUE));
        }
    }

    @Nested
    class OrganizationIdTest {

        @Test
        @DisplayName("parses a well-formed identifier")
        void parsesValid() {
            assertThat(OrganizationId.fromString(VALUE.toString()).value()).isEqualTo(VALUE);
        }

        @Test
        @DisplayName("reports a malformed identifier as a validation failure")
        void rejectsMalformed() {
            assertThatThrownBy(() -> OrganizationId.fromString("nope"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Malformed organization identifier");
        }
    }

    @Test
    @DisplayName("a user id and an organization id are not interchangeable")
    void typesAreDistinct() {
        assertThat((Object) ExternalUserId.of(VALUE))
                .isNotEqualTo(OrganizationId.of(VALUE));
    }
}
