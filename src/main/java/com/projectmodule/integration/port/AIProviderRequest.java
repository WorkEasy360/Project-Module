package com.projectmodule.integration.port;

/**
 * Input to {@link AIProviderPort#generate}. Plain text only — the port itself never sees a
 * {@code Task}, a {@code Risk}, or any other domain type; the caller assembles
 * {@code projectContext} from existing read-only application services before invoking the port,
 * per {@code docs/project/25-AI-ARCHITECTURE-SPEC.md} §3.
 *
 * @param recommendationType the requested {@code RecommendationType}, as its enum name
 * @param projectContext     plain-text context assembled from existing project data
 * @param question           the caller's free-text question, or {@code null} when the
 *                            recommendation type does not take one
 */
public record AIProviderRequest(String recommendationType, String projectContext, String question) {
}
