package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.CreateCustomFieldRequest;
import com.projectmodule.work.api.dto.CustomFieldResponse;
import com.projectmodule.work.api.dto.UpdateCustomFieldRequest;
import com.projectmodule.work.application.ProjectCustomFieldApplicationService;
import com.projectmodule.work.domain.ProjectCustomField;
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
 * REST endpoints for project custom fields, per {@code docs/project/11-CUSTOMFIELD-SPEC.md}
 * (approved) — the "collection nested under a project, item flat" pattern Phase/Milestone/Risk/
 * Issue/Decision already use, with no single-item {@code GET} and no pagination (Decision #9).
 * Holds no business logic or authorization decision; both are delegated to
 * {@link ProjectCustomFieldApplicationService}.
 */
@RestController
public class CustomFieldController {

    private final ProjectCustomFieldApplicationService customFieldApplicationService;
    private final CustomFieldMapper customFieldMapper;
    private final RequestContext requestContext;

    public CustomFieldController(ProjectCustomFieldApplicationService customFieldApplicationService,
                                 CustomFieldMapper customFieldMapper,
                                 RequestContext requestContext) {
        this.customFieldApplicationService = customFieldApplicationService;
        this.customFieldMapper = customFieldMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/projects/{projectId}/custom-fields")
    public ResponseEntity<CustomFieldResponse> createCustomField(@PathVariable UUID projectId,
                                                                  @Valid @RequestBody CreateCustomFieldRequest request) {
        ProjectCustomField created = customFieldApplicationService.createCustomField(requestContext, projectId, request);
        CustomFieldResponse response = customFieldMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/custom-fields/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/projects/{projectId}/custom-fields")
    public List<CustomFieldResponse> listCustomFields(@PathVariable UUID projectId) {
        return customFieldApplicationService.listCustomFields(requestContext, projectId).stream()
                .map(customFieldMapper::toResponse)
                .toList();
    }

    @PatchMapping("/custom-fields/{id}")
    public CustomFieldResponse updateCustomField(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateCustomFieldRequest request) {
        ProjectCustomField updated = customFieldApplicationService.updateCustomField(requestContext, id, request);
        return customFieldMapper.toResponse(updated);
    }

    /** Soft-archives the custom field. See {@link ProjectCustomField#archive}. */
    @DeleteMapping("/custom-fields/{id}")
    public ResponseEntity<Void> archiveCustomField(@PathVariable UUID id) {
        customFieldApplicationService.archiveCustomField(requestContext, id);
        return ResponseEntity.noContent().build();
    }
}
