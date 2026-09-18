package com.projectmodule.work.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * A project's dependency analysis, per {@code docs/project/23-DEPENDENCY-ANALYSIS-SPEC.md} §3.
 * Each inner list of {@code cycles} is one cycle's task ids, in order.
 */
public record DependencyAnalysisResponse(
        List<List<UUID>> cycles,
        List<UUID> blockedTaskIds,
        List<DependencyResponse> brokenDependencies) {
}
