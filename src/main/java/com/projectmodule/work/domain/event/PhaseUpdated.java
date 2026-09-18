package com.projectmodule.work.domain.event;

import java.util.UUID;

/**
 * Raised once by {@code Phase.recordUpdated()}. Corresponds to {@code phase.updated}.
 *
 * <p>Not raised by the individual field mutators, for the same reason {@code ProjectUpdated}
 * is not: a single {@code PATCH} may call several of them, and one event per mutator would
 * produce several audit and outbox records for one logical update.
 */
public record PhaseUpdated(UUID phaseId) implements PhaseDomainEvent {
}
