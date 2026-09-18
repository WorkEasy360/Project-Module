package com.projectmodule.work.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One phase and its nested milestones, per {@code docs/project/18-TIMELINE-SPEC.md}. */
public record TimelinePhaseResponse(
        UUID id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        List<MilestoneResponse> milestones) {
}
