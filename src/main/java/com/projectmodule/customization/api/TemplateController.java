package com.projectmodule.customization.api;

import com.projectmodule.common.api.PageResponse;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.customization.api.dto.ApplyTemplateRequest;
import com.projectmodule.customization.api.dto.CreateTemplateRequest;
import com.projectmodule.customization.api.dto.TemplateResponse;
import com.projectmodule.customization.api.dto.UpdateTemplateRequest;
import com.projectmodule.customization.application.ProjectTemplateApplicationService;
import com.projectmodule.customization.domain.ProjectTemplate;
import com.projectmodule.project.api.ProjectMapper;
import com.projectmodule.project.api.dto.ProjectResponse;
import com.projectmodule.project.domain.Project;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for project templates, per {@code docs/project/12-TEMPLATE-SPEC.md} (approved).
 * Organization-scoped like {@code Project} itself — no organization id in any path; the boundary
 * is resolved from {@link RequestContext}, the same as {@code GET /projects}. Includes a
 * single-item {@code GET}, matching {@code Project} (approved Decision #2), unlike the
 * project-scoped {@code work}/{@code customization.ProjectCustomField} entities.
 *
 * <p>Holds no business logic or authorization decision; both are delegated to
 * {@link ProjectTemplateApplicationService}. {@code apply} maps its result through the
 * <strong>existing</strong> {@link ProjectMapper}/{@link ProjectResponse} — applying a template
 * produces a real {@link Project}, represented exactly as one everywhere else already is.
 */
@RestController
@RequestMapping("/templates")
public class TemplateController {

    private final ProjectTemplateApplicationService templateApplicationService;
    private final TemplateMapper templateMapper;
    private final ProjectMapper projectMapper;
    private final RequestContext requestContext;

    public TemplateController(ProjectTemplateApplicationService templateApplicationService,
                              TemplateMapper templateMapper,
                              ProjectMapper projectMapper,
                              RequestContext requestContext) {
        this.templateApplicationService = templateApplicationService;
        this.templateMapper = templateMapper;
        this.projectMapper = projectMapper;
        this.requestContext = requestContext;
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> createTemplate(@Valid @RequestBody CreateTemplateRequest request) {
        ProjectTemplate created = templateApplicationService.createTemplate(requestContext, request);
        TemplateResponse response = templateMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/templates/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PageResponse<TemplateResponse> listTemplates(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<ProjectTemplate> page = templateApplicationService.listTemplates(requestContext, pageable);
        return PageResponse.of(page, page.map(templateMapper::toResponse).getContent());
    }

    @GetMapping("/{id}")
    public TemplateResponse getTemplate(@PathVariable UUID id) {
        return templateMapper.toResponse(templateApplicationService.getTemplate(requestContext, id));
    }

    @PatchMapping("/{id}")
    public TemplateResponse updateTemplate(@PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
        ProjectTemplate updated = templateApplicationService.updateTemplate(requestContext, id, request);
        return templateMapper.toResponse(updated);
    }

    /** Soft-archives the template. See {@link ProjectTemplate#archive}. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archiveTemplate(@PathVariable UUID id) {
        templateApplicationService.archiveTemplate(requestContext, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Applies the template, creating a new project. Returns the same {@link ProjectResponse}
     * shape {@code POST /api/v1/projects} does, with the {@code Location} header pointing at the
     * new project, not the template.
     */
    @PostMapping("/{id}/apply")
    public ResponseEntity<ProjectResponse> applyTemplate(@PathVariable UUID id,
                                                          @Valid @RequestBody ApplyTemplateRequest request) {
        Project created = templateApplicationService.applyTemplate(requestContext, id, request);
        ProjectResponse response = projectMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/projects/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }
}
