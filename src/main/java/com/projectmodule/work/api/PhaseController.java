package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.CreatePhaseRequest;
import com.projectmodule.work.api.dto.PhaseResponse;
import com.projectmodule.work.api.dto.UpdatePhaseRequest;
import com.projectmodule.work.application.PhaseApplicationService;
import com.projectmodule.work.domain.Phase;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for phases, exactly as {@code docs/project/04-API.md} defines them: the
 * collection nested under a project, the item flat — the same asymmetry {@code Project}'s
 * sub-resources already use. The {@code /api/v1} prefix is applied centrally by
 * {@link com.projectmodule.config.ApiConfiguration}. Holds no business logic or authorization
 * decision; both are delegated to {@link PhaseApplicationService}.
 */
@RestController
public class PhaseController {

    private final PhaseApplicationService phaseApplicationService;
    private final PhaseMapper phaseMapper;
    private final RequestContext requestContext;

    public PhaseController(PhaseApplicationService phaseApplicationService,
                           PhaseMapper phaseMapper,
                           RequestContext requestContext) {
        this.phaseApplicationService = phaseApplicationService;
        this.phaseMapper = phaseMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/projects/{projectId}/phases")
    public ResponseEntity<PhaseResponse> createPhase(@PathVariable UUID projectId,
                                                      @Valid @RequestBody CreatePhaseRequest request) {
        Phase created = phaseApplicationService.createPhase(requestContext, projectId, request);
        PhaseResponse response = phaseMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/phases/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/projects/{projectId}/phases")
    public List<PhaseResponse> listPhases(@PathVariable UUID projectId) {
        return phaseApplicationService.listPhases(requestContext, projectId).stream()
                .map(phaseMapper::toResponse)
                .toList();
    }

    @PatchMapping("/phases/{id}")
    public PhaseResponse updatePhase(@PathVariable UUID id, @Valid @RequestBody UpdatePhaseRequest request) {
        Phase updated = phaseApplicationService.updatePhase(requestContext, id, request);
        return phaseMapper.toResponse(updated);
    }

    /** Soft-archives the phase. See {@link Phase#archive}. */
    @DeleteMapping("/phases/{id}")
    public ResponseEntity<Void> archivePhase(@PathVariable UUID id) {
        phaseApplicationService.archivePhase(requestContext, id);
        return ResponseEntity.noContent().build();
    }
}
