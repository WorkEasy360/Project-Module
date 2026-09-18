package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised once, at the end of {@code Phase.create(...)}. Corresponds to {@code phase.created}. */
public record PhaseCreated(UUID phaseId) implements PhaseDomainEvent {
}
