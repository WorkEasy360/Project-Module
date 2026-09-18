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

class ProjectCustomFieldTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static ProjectCustomField newTextField() {
        return ProjectCustomField.create(PROJECT_ID, "Client Code", CustomFieldValueType.TEXT, "ACME-01");
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts unarchived, belonging to the given project")
        void startsUnarchived() {
            ProjectCustomField field = newTextField();

            assertThat(field.isArchived()).isFalse();
            assertThat(field.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(field.getName()).isEqualTo("Client Code");
            assertThat(field.getValueType()).isEqualTo(CustomFieldValueType.TEXT);
            assertThat(field.getValue()).isEqualTo("ACME-01");
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> ProjectCustomField.create(
                    PROJECT_ID, "  ", CustomFieldValueType.TEXT, "value"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejects a blank value")
        void rejectsBlankValue() {
            assertThatThrownBy(() -> ProjectCustomField.create(
                    PROJECT_ID, "Client Code", CustomFieldValueType.TEXT, "   "))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class ValueTypeValidation {

        @Test
        @DisplayName("TEXT accepts any non-blank value")
        void textAcceptsAnyValue() {
            ProjectCustomField field = ProjectCustomField.create(
                    PROJECT_ID, "Note", CustomFieldValueType.TEXT, "anything at all");

            assertThat(field.getValue()).isEqualTo("anything at all");
        }

        @Test
        @DisplayName("NUMBER accepts a valid number")
        void numberAcceptsValidNumber() {
            ProjectCustomField field = ProjectCustomField.create(
                    PROJECT_ID, "Budget", CustomFieldValueType.NUMBER, "12345.67");

            assertThat(field.getValue()).isEqualTo("12345.67");
        }

        @Test
        @DisplayName("NUMBER rejects a non-numeric value")
        void numberRejectsNonNumeric() {
            assertThatThrownBy(() -> ProjectCustomField.create(
                    PROJECT_ID, "Budget", CustomFieldValueType.NUMBER, "not-a-number"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("DATE accepts a valid ISO-8601 date")
        void dateAcceptsValidIsoDate() {
            ProjectCustomField field = ProjectCustomField.create(
                    PROJECT_ID, "Renewal", CustomFieldValueType.DATE, "2026-03-01");

            assertThat(field.getValue()).isEqualTo("2026-03-01");
        }

        @Test
        @DisplayName("DATE rejects a malformed date")
        void dateRejectsMalformedDate() {
            assertThatThrownBy(() -> ProjectCustomField.create(
                    PROJECT_ID, "Renewal", CustomFieldValueType.DATE, "03/01/2026"))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("BOOLEAN accepts exactly true or false")
        void booleanAcceptsTrueOrFalse() {
            ProjectCustomField trueField = ProjectCustomField.create(
                    PROJECT_ID, "Flagged", CustomFieldValueType.BOOLEAN, "true");
            ProjectCustomField falseField = ProjectCustomField.create(
                    PROJECT_ID, "Flagged", CustomFieldValueType.BOOLEAN, "false");

            assertThat(trueField.getValue()).isEqualTo("true");
            assertThat(falseField.getValue()).isEqualTo("false");
        }

        @Test
        @DisplayName("BOOLEAN rejects anything other than exactly true or false")
        void booleanRejectsOtherValues() {
            assertThatThrownBy(() -> ProjectCustomField.create(
                    PROJECT_ID, "Flagged", CustomFieldValueType.BOOLEAN, "yes"))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class PlainEdits {

        @Test
        @DisplayName("rename and changeValue apply their changes")
        void appliesEdits() {
            ProjectCustomField field = newTextField();

            field.rename("Client Reference");
            field.changeValue("ACME-02");

            assertThat(field.getName()).isEqualTo("Client Reference");
            assertThat(field.getValue()).isEqualTo("ACME-02");
        }

        @Test
        @DisplayName("changeValue re-validates against the field's value type")
        void changeValueRevalidatesType() {
            ProjectCustomField field = ProjectCustomField.create(
                    PROJECT_ID, "Budget", CustomFieldValueType.NUMBER, "100");

            assertThatThrownBy(() -> field.changeValue("not-a-number"))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it")
        void archiveRecordsActor() {
            ProjectCustomField field = newTextField();

            field.archive(ACTOR);

            assertThat(field.isArchived()).isTrue();
            assertThat(field.getArchivedBy()).contains(ACTOR);
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            ProjectCustomField field = newTextField();
            field.archive(ACTOR);

            assertThatThrownBy(() -> field.archive(ACTOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived custom field is read-only")
        void archivedFieldRejectsEdits() {
            ProjectCustomField field = newTextField();
            field.archive(ACTOR);

            assertThatThrownBy(() -> field.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> field.changeValue("New value"))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
