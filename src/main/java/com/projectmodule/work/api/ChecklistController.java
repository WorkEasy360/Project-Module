package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.ChecklistResponse;
import com.projectmodule.work.api.dto.CreateChecklistRequest;
import com.projectmodule.work.api.dto.UpdateChecklistRequest;
import com.projectmodule.work.application.ChecklistApplicationService;
import com.projectmodule.work.domain.Checklist;
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
 * REST endpoints for checklist lines.
 *
 * <p><b>Not specified in {@code docs/project/04-API.md}</b>, like Task List and Subtask — this
 * extends the same documented URL pattern rather than inventing a different shape. Holds no
 * business logic or authorization decision; both are delegated to
 * {@link ChecklistApplicationService}.
 */
@RestController
public class ChecklistController {

    private final ChecklistApplicationService checklistApplicationService;
    private final ChecklistMapper checklistMapper;
    private final RequestContext requestContext;

    public ChecklistController(ChecklistApplicationService checklistApplicationService,
                               ChecklistMapper checklistMapper,
                               RequestContext requestContext) {
        this.checklistApplicationService = checklistApplicationService;
        this.checklistMapper = checklistMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/tasks/{taskId}/checklists")
    public ResponseEntity<ChecklistResponse> createChecklist(@PathVariable UUID taskId,
                                                              @Valid @RequestBody CreateChecklistRequest request) {
        Checklist created = checklistApplicationService.createChecklist(requestContext, taskId, request);
        ChecklistResponse response = checklistMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/checklists/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/tasks/{taskId}/checklists")
    public List<ChecklistResponse> listChecklists(@PathVariable UUID taskId) {
        return checklistApplicationService.listChecklists(requestContext, taskId).stream()
                .map(checklistMapper::toResponse)
                .toList();
    }

    @PatchMapping("/checklists/{id}")
    public ChecklistResponse updateChecklist(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateChecklistRequest request) {
        Checklist updated = checklistApplicationService.updateChecklist(requestContext, id, request);
        return checklistMapper.toResponse(updated);
    }

    /** Soft-archives the checklist line. See {@link Checklist#archive}. */
    @DeleteMapping("/checklists/{id}")
    public ResponseEntity<Void> archiveChecklist(@PathVariable UUID id) {
        checklistApplicationService.archiveChecklist(requestContext, id);
        return ResponseEntity.noContent().build();
    }
}
