package com.projectmodule.work.api;

import com.projectmodule.common.context.RequestContext;
import com.projectmodule.work.api.dto.AIRecommendationResponse;
import com.projectmodule.work.api.dto.RequestRecommendationRequest;
import com.projectmodule.work.application.AIRecommendationApplicationService;
import com.projectmodule.work.domain.AIRecommendation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for AI recommendations, per {@code docs/project/26-AI-RECOMMENDATIONS-SPEC.md}.
 * Holds no business logic or authorization decision; both are delegated to
 * {@link AIRecommendationApplicationService}. A request that reaches an unconfigured AI provider
 * returns the standard 503 {@code ProblemDetail} — see
 * {@code docs/project/25-AI-ARCHITECTURE-SPEC.md} §6.
 */
@RestController
public class AIRecommendationController {

    private final AIRecommendationApplicationService aiRecommendationApplicationService;
    private final AIRecommendationMapper aiRecommendationMapper;
    private final RequestContext requestContext;

    public AIRecommendationController(AIRecommendationApplicationService aiRecommendationApplicationService,
                                      AIRecommendationMapper aiRecommendationMapper,
                                      RequestContext requestContext) {
        this.aiRecommendationApplicationService = aiRecommendationApplicationService;
        this.aiRecommendationMapper = aiRecommendationMapper;
        this.requestContext = requestContext;
    }

    @PostMapping("/projects/{projectId}/ai/recommendations")
    public ResponseEntity<AIRecommendationResponse> requestRecommendation(
            @PathVariable UUID projectId, @Valid @RequestBody RequestRecommendationRequest request) {
        AIRecommendation created = aiRecommendationApplicationService.requestRecommendation(
                requestContext, projectId, request.type(), request.resourceId(), request.question());
        AIRecommendationResponse response = aiRecommendationMapper.toResponse(created);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/ai/recommendations/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/ai/recommendations/{id}")
    public AIRecommendationResponse getRecommendation(@PathVariable UUID id) {
        return aiRecommendationMapper.toResponse(aiRecommendationApplicationService.getRecommendation(requestContext, id));
    }

    @GetMapping("/projects/{projectId}/ai/recommendations")
    public List<AIRecommendationResponse> listRecommendations(@PathVariable UUID projectId) {
        return aiRecommendationApplicationService.listRecommendations(requestContext, projectId).stream()
                .map(aiRecommendationMapper::toResponse)
                .toList();
    }

    @PostMapping("/ai/recommendations/{id}/accept")
    public AIRecommendationResponse acceptRecommendation(@PathVariable UUID id) {
        AIRecommendation accepted = aiRecommendationApplicationService.acceptRecommendation(requestContext, id);
        return aiRecommendationMapper.toResponse(accepted);
    }

    @PostMapping("/ai/recommendations/{id}/reject")
    public AIRecommendationResponse rejectRecommendation(@PathVariable UUID id) {
        AIRecommendation rejected = aiRecommendationApplicationService.rejectRecommendation(requestContext, id);
        return aiRecommendationMapper.toResponse(rejected);
    }
}
