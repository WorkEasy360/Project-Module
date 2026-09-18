package com.projectmodule.project.domain.event;

import java.util.UUID;

/** Raised once, at the end of {@code Project.create(...)}. Corresponds to {@code project.created}. */
public record ProjectCreated(UUID projectId) implements ProjectDomainEvent {
}
