package com.projectmodule.assistant.api;

import com.projectmodule.assistant.application.AssistantApplicationService;
import com.projectmodule.common.context.RequestContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The in-app assistant. */
@RestController
@RequestMapping("/assistant")
public class AssistantController {

    public record ChatRequest(UUID conversationId, @NotBlank @Size(max = 4000) String message,
                              @Size(max = 300) String context) {
    }

    public record DecisionRequest(boolean approve) {
    }

    private final AssistantApplicationService assistant;
    private final RequestContext requestContext;

    public AssistantController(AssistantApplicationService assistant, RequestContext requestContext) {
        this.assistant = assistant;
        this.requestContext = requestContext;
    }

    /** Whether the assistant can be used. Needs sign-in like everything else. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        requestContext.requireUserId();
        return Map.of("enabled", assistant.isEnabled());
    }

    @PostMapping("/chat")
    public AssistantApplicationService.Reply chat(@Valid @RequestBody ChatRequest request) {
        return assistant.chat(requestContext, request.conversationId(), request.message(), request.context());
    }

    @PostMapping("/conversations/{id}/decision")
    public AssistantApplicationService.Reply decide(@PathVariable UUID id, @RequestBody DecisionRequest request) {
        return assistant.decide(requestContext, id, request.approve());
    }
}
