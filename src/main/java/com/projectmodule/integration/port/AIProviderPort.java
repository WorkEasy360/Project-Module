package com.projectmodule.integration.port;

import com.projectmodule.common.exception.IntegrationUnavailableException;

/**
 * Outbound port to an external AI/LLM provider — the integration boundary named in
 * {@code docs/project/25-AI-ARCHITECTURE-SPEC.md} §3. No provider is chosen or bundled in this
 * module (§4 of that document is an explicitly open decision); the only implementation present
 * is {@code UnavailableAIProviderAdapter}.
 *
 * <p>This port never touches a repository or the database — it only ever sees the plain text
 * its caller assembled from existing read-only application services. AI never reaches the
 * database directly, at any layer, including this one.
 */
public interface AIProviderPort {

    /**
     * Generates a recommendation's content.
     *
     * @throws IntegrationUnavailableException if no AI provider is configured for this
     *                                          deployment
     */
    AIProviderResponse generate(AIProviderRequest request);
}
