package com.projectmodule.customization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.domain.ProjectPriority;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProjectTemplateTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId ACTOR = ExternalUserId.of(UUID.randomUUID());

    private static ProjectTemplate newTemplate() {
        return ProjectTemplate.create(ORG, "Standard Sprint", ProjectPriority.MEDIUM);
    }

    @Nested
    class Creation {

        @Test
        @DisplayName("starts unarchived, belonging to the given organization")
        void startsUnarchived() {
            ProjectTemplate template = newTemplate();

            assertThat(template.isArchived()).isFalse();
            assertThat(template.getOrganizationId()).isEqualTo(ORG);
            assertThat(template.getName()).isEqualTo("Standard Sprint");
            assertThat(template.getDefaultPriority()).contains(ProjectPriority.MEDIUM);
        }

        @Test
        @DisplayName("defaultPriority is optional")
        void defaultPriorityIsOptional() {
            ProjectTemplate template = ProjectTemplate.create(ORG, "Bare Preset", null);

            assertThat(template.getDefaultPriority()).isEmpty();
        }

        @Test
        @DisplayName("rejects a blank name")
        void rejectsBlankName() {
            assertThatThrownBy(() -> ProjectTemplate.create(ORG, "  ", ProjectPriority.MEDIUM))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class PlainEdits {

        @Test
        @DisplayName("plain field mutators apply their changes")
        void appliesEdits() {
            ProjectTemplate template = newTemplate();

            template.rename("Client Onboarding");
            template.describe("Used for new client kickoffs");
            template.changeDefaultPriority(ProjectPriority.HIGH);

            assertThat(template.getName()).isEqualTo("Client Onboarding");
            assertThat(template.getDescription()).contains("Used for new client kickoffs");
            assertThat(template.getDefaultPriority()).contains(ProjectPriority.HIGH);
        }
    }

    @Nested
    class Archiving {

        @Test
        @DisplayName("archiving records who did it")
        void archiveRecordsActor() {
            ProjectTemplate template = newTemplate();

            template.archive(ACTOR);

            assertThat(template.isArchived()).isTrue();
            assertThat(template.getArchivedBy()).contains(ACTOR);
        }

        @Test
        @DisplayName("refuses to archive twice")
        void refusesDoubleArchive() {
            ProjectTemplate template = newTemplate();
            template.archive(ACTOR);

            assertThatThrownBy(() -> template.archive(ACTOR))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("an archived template is read-only")
        void archivedTemplateRejectsEdits() {
            ProjectTemplate template = newTemplate();
            template.archive(ACTOR);

            assertThatThrownBy(() -> template.rename("New name"))
                    .isInstanceOf(BusinessRuleViolationException.class);
            assertThatThrownBy(() -> template.changeDefaultPriority(ProjectPriority.LOW))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
