package com.projectmodule.project.api;

import com.projectmodule.common.api.PageResponse;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.api.dto.ProjectResponse;
import com.projectmodule.project.api.dto.ProjectSummaryResponse;
import com.projectmodule.project.api.dto.UpdateProjectRequest;
import com.projectmodule.project.application.ProjectApplicationService;
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
 * REST endpoints for project CRUD.
 *
 * <p>The {@code /api/v1} prefix is applied centrally by
 * {@link com.projectmodule.config.ApiConfiguration}, not repeated here.
 *
 * <p>Holds no business logic: every method reads the request, delegates to
 * {@link ProjectApplicationService}, and maps the result. Validation of request shape is
 * {@code @Valid}; validation of domain rules happens in the application and domain layers and
 * is reported through {@link com.projectmodule.common.api.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectApplicationService projectApplicationService;
    private final ProjectMapper projectMapper;
    private final RequestContext requestContext;

    public ProjectController(ProjectApplicationService projectApplicationService,
                             ProjectMapper projectMapper,
                             RequestContext requestContext) {
        this.projectApplicationService = projectApplicationService;
        this.projectMapper = projectMapper;
        this.requestContext = requestContext;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        Project created = projectApplicationService.createProject(requestContext, request);
        ProjectResponse response = projectMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PageResponse<ProjectSummaryResponse> listProjects(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<Project> page = projectApplicationService.listProjects(requestContext, pageable);
        return PageResponse.of(page, page.map(projectMapper::toSummary).getContent());
    }

    @GetMapping("/{id}")
    public ProjectResponse getProject(@PathVariable UUID id) {
        Project project = projectApplicationService.getProject(requestContext, id);
        return projectMapper.toResponse(project);
    }

    @PatchMapping("/{id}")
    public ProjectResponse updateProject(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateProjectRequest request) {
        Project updated = projectApplicationService.updateProject(requestContext, id, request);
        return projectMapper.toResponse(updated);
    }

    /** Soft-archives the project. See {@link Project#archive}. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archiveProject(@PathVariable UUID id) {
        projectApplicationService.archiveProject(requestContext, id);
        return ResponseEntity.noContent().build();
    }
}
