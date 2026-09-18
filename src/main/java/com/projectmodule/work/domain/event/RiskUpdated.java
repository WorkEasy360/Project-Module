package com.projectmodule.work.domain.event;

import java.util.UUID;

/**
 * Raised once by {@code Risk.recordUpdated()}. Corresponds to {@code risk.updated}.
 *
 * <p>Not raised by the individual field mutators, for the same reason {@code TaskUpdated} is
 * not: a single {@code PATCH} may call several of them, and this raises exactly one event
 * regardless.
 */
public record RiskUpdated(UUID riskId) implements RiskDomainEvent {
}
