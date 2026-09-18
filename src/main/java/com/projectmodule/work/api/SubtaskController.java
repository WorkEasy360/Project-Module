package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.CreateSubtaskRequest;
import com.projectmodule.work.api.dto.SubtaskResponse;
import com.projectmodule.work.api.dto.UpdateSubtaskRequest;
import com.projectmodule.work.application.SubtaskApplicationService;
import com.projectmodule.work.domain.Subtask;
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
 * REST endpoints for subtasks.
 *
 * <p><b>Not specified in {@code docs/project/04-API.md}</b>, like Task List last slice — this
 * extends the same documented URL pattern (collection nested under the parent, item flat)
 * rather than inventing a different shape. Holds no business logic or authorization decision;
 * both are delegated to {@link SubtaskApplicationService}.
 */
@RestController
public class SubtaskController {

    private final SubtaskApplicationService subtaskApplicationService;
    private final SubtaskMapper subtaskMapper;
    private final RequestContext requestContext;

    public SubtaskController(SubtaskApplicationService subtaskApplicationService,
                             SubtaskMapper subtaskMapper,
                             RequestContext requestContext) {
        this.subtaskApplicationService = subtaskApplicationService;
        this.subtaskMapper = subtaskMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/tasks/{taskId}/subtasks")
    public ResponseEntity<SubtaskResponse> createSubtask(@PathVariable UUID taskId,
                                                          @Valid @RequestBody CreateSubtaskRequest request) {
        Subtask created = subtaskApplicationService.createSubtask(requestContext, taskId, request);
        SubtaskResponse response = subtaskMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/subtasks/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/tasks/{taskId}/subtasks")
    public List<SubtaskResponse> listSubtasks(@PathVariable UUID taskId) {
        return subtaskApplicationService.listSubtasks(requestContext, taskId).stream()
                .map(subtaskMapper::toResponse)
                .toList();
    }

    @PatchMapping("/subtasks/{id}")
    public SubtaskResponse updateSubtask(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateSubtaskRequest request) {
        Subtask updated = subtaskApplicationService.updateSubtask(requestContext, id, request);
        return subtaskMapper.toResponse(updated);
    }

    /** Soft-archives the subtask. See {@link Subtask#archive}. */
    @DeleteMapping("/subtasks/{id}")
    public ResponseEntity<Void> archiveSubtask(@PathVariable UUID id) {
        subtaskApplicationService.archiveSubtask(requestContext, id);
        return ResponseEntity.noContent().build();
    }
}
