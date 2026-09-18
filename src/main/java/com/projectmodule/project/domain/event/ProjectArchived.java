package com.projectmodule.project.domain.event;

import java.util.UUID;

/** Raised once, at the end of {@code Project.archive(...)}. Corresponds to {@code project.archived}. */
public record ProjectArchived(UUID projectId) implements ProjectDomainEvent {
}
