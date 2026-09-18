package com.projectmodule.integration.port;

/**
 * Output of {@link AIProviderPort#generate}, per
 * {@code docs/project/25-AI-ARCHITECTURE-SPEC.md} §3.
 *
 * @param title     short label of what was suggested
 * @param rationale why it was suggested
 * @param payload   structured suggestion content as JSON text, or {@code null} when the
 *                  recommendation is pure narrative
 */
public record AIProviderResponse(String title, String rationale, String payload) {
}
