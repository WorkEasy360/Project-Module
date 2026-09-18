package com.projectmodule.integration.adapter;

import com.projectmodule.common.exception.IntegrationUnavailableException;
import com.projectmodule.integration.port.AIProviderPort;
import com.projectmodule.integration.port.AIProviderRequest;
import com.projectmodule.integration.port.AIProviderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Honest placeholder for {@link AIProviderPort}: no LLM provider is configured for this
 * deployment (see {@code docs/project/25-AI-ARCHITECTURE-SPEC.md} §4, an explicitly open
 * decision). Unlike {@link NoopCalendarAdapter}/{@link NoopDocumentAdapter} — which can safely
 * no-op, since "nothing happened" is a valid outcome for a calendar sync — an AI provider cannot
 * safely return empty or placeholder content: that would be exactly the fake AI response this
 * module's rules forbid. This adapter therefore always fails clearly rather than fabricating a
 * response.
 */
@Component
public class UnavailableAIProviderAdapter implements AIProviderPort {

    private static final Logger log = LoggerFactory.getLogger(UnavailableAIProviderAdapter.class);

    @Override
    public AIProviderResponse generate(AIProviderRequest request) {
        log.debug("AIProviderPort.generate(): no AI provider is configured; recommendationType={}",
                request.recommendationType());
        throw new IntegrationUnavailableException("AI provider", null);
    }
}
