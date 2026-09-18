package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.CreateDependencyRequest;
import com.projectmodule.work.api.dto.DependencyResponse;
import com.projectmodule.work.api.dto.UpdateDependencyRequest;
import com.projectmodule.work.application.DependencyApplicationService;
import com.projectmodule.work.domain.Dependency;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for dependencies.
 *
 * <p><b>Not specified in {@code docs/project/04-API.md}</b>, like Subtask and Checklist last
 * slice — this extends the same documented URL pattern rather than inventing a different shape.
 * Holds no business logic or authorization decision; both are delegated to
 * {@link DependencyApplicationService}.
 */
@RestController
public class DependencyController {

    private final DependencyApplicationService dependencyApplicationService;
    private final DependencyMapper dependencyMapper;
    private final RequestContext requestContext;

    public DependencyController(DependencyApplicationService dependencyApplicationService,
                                DependencyMapper dependencyMapper,
                                RequestContext requestContext) {
        this.dependencyApplicationService = dependencyApplicationService;
        this.dependencyMapper = dependencyMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/tasks/{taskId}/dependencies")
    public ResponseEntity<DependencyResponse> createDependency(@PathVariable UUID taskId,
                                                                @Valid @RequestBody CreateDependencyRequest request) {
        Dependency created = dependencyApplicationService.createDependency(requestContext, taskId, request);
        DependencyResponse response = dependencyMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/dependencies/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/tasks/{taskId}/dependencies")
    public List<DependencyResponse> listDependencies(@PathVariable UUID taskId) {
        return dependencyApplicationService.listDependencies(requestContext, taskId).stream()
                .map(dependencyMapper::toResponse)
                .toList();
    }

    @PatchMapping("/dependencies/{id}")
    public DependencyResponse updateDependency(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateDependencyRequest request) {
        Dependency updated = dependencyApplicationService.updateDependency(requestContext, id, request);
        return dependencyMapper.toResponse(updated);
    }

    /** Soft-archives the dependency. See {@link Dependency#archive}. */
    @DeleteMapping("/dependencies/{id}")
    public ResponseEntity<Void> archiveDependency(@PathVariable UUID id) {
        dependencyApplicationService.archiveDependency(requestContext, id);
        return ResponseEntity.noContent().build();
    }
}
