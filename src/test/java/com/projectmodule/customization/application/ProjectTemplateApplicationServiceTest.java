package com.projectmodule.customization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.customization.api.dto.ApplyTemplateRequest;
import com.projectmodule.customization.api.dto.CreateTemplateRequest;
import com.projectmodule.customization.api.dto.UpdateTemplateRequest;
import com.projectmodule.customization.domain.ProjectTemplate;
import com.projectmodule.customization.infrastructure.ProjectTemplateRepository;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProjectTemplateApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final OrganizationId OTHER_ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId USER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectTemplateRepository templateRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    private ProjectTemplateApplicationService service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new ProjectTemplateApplicationService(templateRepository, projectApplicationService);
        context = new ImmutableRequestContext(
                Optional.of(USER), Optional.of(ORG), "corr-1", ActorType.HUMAN);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a template in the caller's organization")
        void createsTemplate() {
            when(templateRepository.existsByOrganizationIdAndNameAndArchivedAtIsNull(ORG.value(), "Standard Sprint"))
                    .thenReturn(false);
            when(templateRepository.save(any(ProjectTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateTemplateRequest request = new CreateTemplateRequest(
                    "Standard Sprint", "For recurring sprints", ProjectPriority.MEDIUM);
            ProjectTemplate created = service.createTemplate(context, request);

            assertThat(created.getOrganizationId()).isEqualTo(ORG);
            assertThat(created.getName()).isEqualTo("Standard Sprint");
            assertThat(created.getDefaultPriority()).contains(ProjectPriority.MEDIUM);
        }

        @Test
        @DisplayName("rejects a duplicate active name within the same organization")
        void rejectsDuplicateName() {
            when(templateRepository.existsByOrganizationIdAndNameAndArchivedAtIsNull(ORG.value(), "Standard Sprint"))
                    .thenReturn(true);

            CreateTemplateRequest request = new CreateTemplateRequest("Standard Sprint", null, null);

            assertThatThrownBy(() -> service.createTemplate(context, request))
                    .isInstanceOf(ConflictException.class);
            verify(templateRepository, never()).save(any());
        }
    }

    @Nested
    class GetAndList {

        @Test
        @DisplayName("returns a template within the caller's organization")
        void returnsTemplate() {
            ProjectTemplate template = ProjectTemplate.create(ORG, "Standard Sprint", ProjectPriority.MEDIUM);
            when(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).thenReturn(Optional.of(template));

            assertThat(service.getTemplate(context, template.getId())).isEqualTo(template);
        }

        @Test
        @DisplayName("reports not found for a template belonging to a different organization")
        void deniesCrossOrganization() {
            ProjectTemplate template = ProjectTemplate.create(OTHER_ORG, "Standard Sprint", ProjectPriority.MEDIUM);
            when(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).thenReturn(Optional.of(template));

            assertThatThrownBy(() -> service.getTemplate(context, template.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("reports not found for a missing template")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(templateRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTemplate(context, id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("lists a page of templates for the caller's organization")
        void listsTemplates() {
            ProjectTemplate template = ProjectTemplate.create(ORG, "Standard Sprint", ProjectPriority.MEDIUM);
            Pageable pageable = PageRequest.of(0, 20);
            when(templateRepository.findByOrganizationIdAndArchivedAtIsNull(ORG.value(), pageable))
                    .thenReturn(new PageImpl<>(java.util.List.of(template), pageable, 1));

            assertThat(service.listTemplates(context, pageable).getContent()).containsExactly(template);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("applies a plain field edit")
        void appliesPlainEdit() {
            ProjectTemplate template = ProjectTemplate.create(ORG, "Standard Sprint", ProjectPriority.MEDIUM);
            when(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).thenReturn(Optional.of(template));
            when(templateRepository.save(any(ProjectTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateTemplateRequest request = new UpdateTemplateRequest(
                    "Renamed Sprint", null, ProjectPriority.HIGH, template.getVersion());
            ProjectTemplate updated = service.updateTemplate(context, template.getId(), request);

            assertThat(updated.getName()).isEqualTo("Renamed Sprint");
            assertThat(updated.getDefaultPriority()).contains(ProjectPriority.HIGH);
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            ProjectTemplate template = ProjectTemplate.create(ORG, "Standard Sprint", ProjectPriority.MEDIUM);
            when(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).thenReturn(Optional.of(template));

            UpdateTemplateRequest request = new UpdateTemplateRequest(
                    "Renamed", null, null, template.getVersion() + 1);

            assertThatThrownBy(() -> service.updateTemplate(context, template.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(templateRepository, never()).save(any());
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("archives the template")
        void archivesTemplate() {
            ProjectTemplate template = ProjectTemplate.create(ORG, "Standard Sprint", ProjectPriority.MEDIUM);
            when(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).thenReturn(Optional.of(template));
            when(templateRepository.save(any(ProjectTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveTemplate(context, template.getId());

            assertThat(template.isArchived()).isTrue();
        }
    }

    @Nested
    class Apply {

        @Test
        @DisplayName("delegates to ProjectApplicationService.createProject with the template's defaultPriority")
        void delegatesToProjectCreation() {
            ProjectTemplate template = ProjectTemplate.create(ORG, "Standard Sprint", ProjectPriority.HIGH);
            template.describe("Template description");
            when(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).thenReturn(Optional.of(template));

            Project createdProject = Project.create(ORG, "New Sprint Project", USER, ProjectPriority.HIGH);
            when(projectApplicationService.createProject(eq(context), any(CreateProjectRequest.class)))
                    .thenReturn(createdProject);

            ApplyTemplateRequest request = new ApplyTemplateRequest("New Sprint Project", null);
            Project result = service.applyTemplate(context, template.getId(), request);

            assertThat(result).isEqualTo(createdProject);

            org.mockito.ArgumentCaptor<CreateProjectRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(CreateProjectRequest.class);
            verify(projectApplicationService).createProject(eq(context), captor.capture());
            assertThat(captor.getValue().name()).isEqualTo("New Sprint Project");
            assertThat(captor.getValue().description()).isEqualTo("Template description");
            assertThat(captor.getValue().priority()).isEqualTo(ProjectPriority.HIGH);
        }

        @Test
        @DisplayName("an explicit description in the request overrides the template's own description")
        void descriptionOverride() {
            ProjectTemplate template = ProjectTemplate.create(ORG, "Standard Sprint", ProjectPriority.HIGH);
            template.describe("Template description");
            when(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).thenReturn(Optional.of(template));
            when(projectApplicationService.createProject(eq(context), any(CreateProjectRequest.class)))
                    .thenReturn(Project.create(ORG, "New Project", USER, ProjectPriority.HIGH));

            ApplyTemplateRequest request = new ApplyTemplateRequest("New Project", "Overridden description");
            service.applyTemplate(context, template.getId(), request);

            org.mockito.ArgumentCaptor<CreateProjectRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(CreateProjectRequest.class);
            verify(projectApplicationService).createProject(eq(context), captor.capture());
            assertThat(captor.getValue().description()).isEqualTo("Overridden description");
        }

        @Test
        @DisplayName("rejects applying a template with no defaultPriority")
        void rejectsMissingDefaultPriority() {
            ProjectTemplate template = ProjectTemplate.create(ORG, "Bare Preset", null);
            when(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).thenReturn(Optional.of(template));

            ApplyTemplateRequest request = new ApplyTemplateRequest("New Project", null);

            assertThatThrownBy(() -> service.applyTemplate(context, template.getId(), request))
                    .isInstanceOf(BusinessRuleViolationException.class);
            verify(projectApplicationService, never()).createProject(any(), any());
        }

        @Test
        @DisplayName("rejects applying an archived template")
        void rejectsArchivedTemplate() {
            ProjectTemplate template = ProjectTemplate.create(ORG, "Standard Sprint", ProjectPriority.HIGH);
            template.archive(USER);
            when(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).thenReturn(Optional.empty());

            ApplyTemplateRequest request = new ApplyTemplateRequest("New Project", null);

            assertThatThrownBy(() -> service.applyTemplate(context, template.getId(), request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(projectApplicationService, never()).createProject(any(), any());
        }

        @Test
        @DisplayName("rejects applying a template across an organization boundary")
        void rejectsCrossOrganization() {
            ProjectTemplate template = ProjectTemplate.create(OTHER_ORG, "Standard Sprint", ProjectPriority.HIGH);
            when(templateRepository.findByIdAndArchivedAtIsNull(template.getId())).thenReturn(Optional.of(template));

            ApplyTemplateRequest request = new ApplyTemplateRequest("New Project", null);

            assertThatThrownBy(() -> service.applyTemplate(context, template.getId(), request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(projectApplicationService, never()).createProject(any(), any());
        }
    }
}
