package com.projectmodule.work.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.work.api.dto.CreateCustomFieldRequest;
import com.projectmodule.work.api.dto.UpdateCustomFieldRequest;
import com.projectmodule.work.domain.ProjectCustomField;
import com.projectmodule.work.infrastructure.ProjectCustomFieldRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for project custom field CRUD.
 *
 * <p>Same tenant/authorization pattern as {@link RiskApplicationService}/
 * {@link IssueApplicationService}/{@link DecisionApplicationService}: the owning project is
 * resolved through {@code ProjectApplicationService.findActiveProjectInCallerOrganization}, and
 * every mutation is gated by the existing {@link ProjectAuthorizationService} — no second
 * authorization system. Unlike {@code ProjectComment}, a custom field is project configuration
 * data, not a personal remark, so authorization here is fully permission-based: {@code
 * EDIT_PROJECT} for create/update/archive, {@code VIEW_PROJECT} for list.
 *
 * <p>Like {@link IssueApplicationService}/{@link DecisionApplicationService}, this has no
 * {@link WorkEventRecorder} dependency: the approved specification
 * ({@code docs/project/11-CUSTOMFIELD-SPEC.md}) defines no CustomField events, so there is
 * nothing to record.
 *
 * <p>Active-name uniqueness per project is enforced at the database level by a partial unique
 * index ({@code V9__create_project_custom_fields.sql}, Decision #7). The pre-check here exists
 * only to raise the same {@link ConflictException} every other duplicate-protection rule in this
 * module raises (e.g. {@code DependencyApplicationService}), rather than surfacing a raw
 * constraint-violation error to the caller; the index remains the source of truth under
 * concurrent writes.
 */
@Service
public class ProjectCustomFieldApplicationService {

    private final ProjectCustomFieldRepository customFieldRepository;
    private final ProjectApplicationService projectApplicationService;
    private final ProjectAuthorizationService projectAuthorizationService;

    public ProjectCustomFieldApplicationService(ProjectCustomFieldRepository customFieldRepository,
                                                ProjectApplicationService projectApplicationService,
                                                ProjectAuthorizationService projectAuthorizationService) {
        this.customFieldRepository = customFieldRepository;
        this.projectApplicationService = projectApplicationService;
        this.projectAuthorizationService = projectAuthorizationService;
    }

    @Transactional
    public ProjectCustomField createCustomField(RequestContext context, UUID projectId, CreateCustomFieldRequest request) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.EDIT_PROJECT);

        if (customFieldRepository.existsByProjectIdAndNameAndArchivedAtIsNull(projectId, request.name().trim())) {
            throw new ConflictException(
                    "A custom field named \"" + request.name().trim() + "\" already exists on this project");
        }

        ProjectCustomField customField = ProjectCustomField.create(
                projectId, request.name(), request.valueType(), request.value());

        return customFieldRepository.save(customField);
    }

    @Transactional(readOnly = true)
    public List<ProjectCustomField> listCustomFields(RequestContext context, UUID projectId) {
        projectApplicationService.findActiveProjectInCallerOrganization(context, projectId);
        projectAuthorizationService.requirePermission(
                context.requireUserId(), projectId, ProjectPermission.VIEW_PROJECT);
        return customFieldRepository.findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(projectId);
    }

    @Transactional
    public ProjectCustomField updateCustomField(RequestContext context, UUID customFieldId, UpdateCustomFieldRequest request) {
        ProjectCustomField customField = findActiveCustomField(customFieldId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, customField.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), customField.getProjectId(), ProjectPermission.EDIT_PROJECT);

        if (customField.getVersion() != request.version()) {
            throw new ConflictException(
                    "Custom field " + customFieldId + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        applyUpdates(customField, request);

        return customFieldRepository.save(customField);
    }

    @Transactional
    public void archiveCustomField(RequestContext context, UUID customFieldId) {
        ProjectCustomField customField = findActiveCustomField(customFieldId);
        projectApplicationService.findActiveProjectInCallerOrganization(context, customField.getProjectId());
        projectAuthorizationService.requirePermission(
                context.requireUserId(), customField.getProjectId(), ProjectPermission.EDIT_PROJECT);

        customField.archive(context.requireUserId());
        customFieldRepository.save(customField);
    }

    private void applyUpdates(ProjectCustomField customField, UpdateCustomFieldRequest request) {
        if (request.name() != null) {
            customField.rename(request.name());
        }
        if (request.value() != null) {
            customField.changeValue(request.value());
        }
    }

    /**
     * Finds a custom field by id. Unlike {@code ProjectApplicationService}'s equivalent, this
     * does not also check the caller's organization: {@code PATCH}/{@code DELETE
     * /custom-fields/:id} carry no project id, so the owning project is not known until after
     * this lookup.
     */
    private ProjectCustomField findActiveCustomField(UUID customFieldId) {
        return customFieldRepository.findByIdAndArchivedAtIsNull(customFieldId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomField", customFieldId));
    }
}
