package com.projectmodule.customization.application;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.customization.api.dto.ApplyTemplateRequest;
import com.projectmodule.customization.api.dto.CreateTemplateRequest;
import com.projectmodule.customization.api.dto.UpdateTemplateRequest;
import com.projectmodule.customization.domain.ProjectTemplate;
import com.projectmodule.customization.infrastructure.ProjectTemplateRepository;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.domain.Project;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for project template CRUD, and for applying a template to create a new
 * {@link Project}.
 *
 * <p>Organization-scoped, the same convention {@code Project} itself uses — resolved via
 * {@code context.organizationId()}, mirroring {@code ProjectApplicationService}'s own private
 * {@code requireOrganizationId} helper (duplicated here rather than shared, the same way every
 * existing application service already resolves this independently; there is no shared utility
 * for it anywhere in this codebase).
 *
 * <p>Per the approved specification ({@code docs/project/12-TEMPLATE-SPEC.md}, Decision #1),
 * authorization is <em>not</em> permission-based: there is no organization-level role system in
 * this codebase (no {@code OrganizationRole}/{@code OrganizationMember}), so this service adds
 * no permission check of its own, the same as {@code ProjectApplicationService.createProject}
 * adds none. <em>Who</em> may reach these operations at all is governed by the upstream
 * gateway/application contract that supplies {@link RequestContext} — this service trusts it,
 * the same trust boundary every existing entity's organization-boundary check already rests on.
 *
 * <p>Like {@link com.projectmodule.work.application.IssueApplicationService}, this has no
 * {@code WorkEventRecorder}-equivalent dependency: the approved specification defines no
 * Template events, so there is nothing to record for the template record itself.
 *
 * <p>{@link #applyTemplate} does not duplicate {@code Project} creation logic: it builds a
 * {@link CreateProjectRequest} from the template and delegates entirely to
 * {@link ProjectApplicationService#createProject}, so the resulting project goes through the
 * exact same organization-boundary enforcement, {@code OWNER} assignment via {@code ProjectMember},
 * and {@code project.created} event pipeline as any other project creation.
 */
@Service
public class ProjectTemplateApplicationService {

    private final ProjectTemplateRepository templateRepository;
    private final ProjectApplicationService projectApplicationService;

    public ProjectTemplateApplicationService(ProjectTemplateRepository templateRepository,
                                             ProjectApplicationService projectApplicationService) {
        this.templateRepository = templateRepository;
        this.projectApplicationService = projectApplicationService;
    }

    @Transactional
    public ProjectTemplate createTemplate(RequestContext context, CreateTemplateRequest request) {
        OrganizationId organizationId = requireOrganizationId(context);

        if (templateRepository.existsByOrganizationIdAndNameAndArchivedAtIsNull(
                organizationId.value(), request.name().trim())) {
            throw new ConflictException(
                    "A template named \"" + request.name().trim() + "\" already exists in this organization");
        }

        ProjectTemplate template = ProjectTemplate.create(organizationId, request.name(), request.defaultPriority());
        template.describe(request.description());

        return templateRepository.save(template);
    }

    @Transactional(readOnly = true)
    public ProjectTemplate getTemplate(RequestContext context, UUID id) {
        return findActiveTemplateInCallerOrganization(context, id);
    }

    @Transactional(readOnly = true)
    public Page<ProjectTemplate> listTemplates(RequestContext context, Pageable pageable) {
        OrganizationId organizationId = requireOrganizationId(context);
        return templateRepository.findByOrganizationIdAndArchivedAtIsNull(organizationId.value(), pageable);
    }

    @Transactional
    public ProjectTemplate updateTemplate(RequestContext context, UUID id, UpdateTemplateRequest request) {
        ProjectTemplate template = findActiveTemplateInCallerOrganization(context, id);

        if (template.getVersion() != request.version()) {
            throw new ConflictException(
                    "Template " + id + " has changed since version " + request.version()
                            + "; reload and try again");
        }

        applyUpdates(template, request);

        return templateRepository.save(template);
    }

    @Transactional
    public void archiveTemplate(RequestContext context, UUID id) {
        ProjectTemplate template = findActiveTemplateInCallerOrganization(context, id);
        template.archive(context.requireUserId());
        templateRepository.save(template);
    }

    /**
     * Applies a template: creates a new {@link Project} pre-filled from the template's
     * defaults. Delegates entirely to {@link ProjectApplicationService#createProject} — see the
     * class-level documentation.
     *
     * @throws BusinessRuleViolationException if the template has no {@code defaultPriority} set,
     *                                         since {@code Project} creation requires one and no
     *                                         override is accepted here (approved specification:
     *                                         "priority comes from template.defaultPriority")
     */
    @Transactional
    public Project applyTemplate(RequestContext context, UUID templateId, ApplyTemplateRequest request) {
        ProjectTemplate template = findActiveTemplateInCallerOrganization(context, templateId);

        var defaultPriority = template.getDefaultPriority().orElseThrow(() -> new BusinessRuleViolationException(
                "Template \"" + template.getName() + "\" has no defaultPriority set and cannot be applied"));

        String description = request.description() != null
                ? request.description()
                : template.getDescription().orElse(null);

        CreateProjectRequest createProjectRequest =
                new CreateProjectRequest(request.name(), description, defaultPriority, null, null);

        return projectApplicationService.createProject(context, createProjectRequest);
    }

    private void applyUpdates(ProjectTemplate template, UpdateTemplateRequest request) {
        if (request.name() != null) {
            template.rename(request.name());
        }
        if (request.description() != null) {
            template.describe(request.description());
        }
        if (request.defaultPriority() != null) {
            template.changeDefaultPriority(request.defaultPriority());
        }
    }

    /**
     * Finds a template by id, scoped to the caller's organization. An archived template is
     * reported not found, the same as one that never existed, the same convention
     * {@code ProjectApplicationService.findActiveProjectInCallerOrganization} uses — this is
     * also what makes an archived template unable to be applied (approved specification).
     */
    private ProjectTemplate findActiveTemplateInCallerOrganization(RequestContext context, UUID id) {
        OrganizationId organizationId = requireOrganizationId(context);

        ProjectTemplate template = templateRepository.findByIdAndArchivedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template", id));

        if (!template.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Template", id);
        }

        return template;
    }

    private static OrganizationId requireOrganizationId(RequestContext context) {
        return context.organizationId().orElseThrow(() -> new MissingIdentityException(
                "No organization was supplied with this request"));
    }
}
