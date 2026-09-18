package com.projectmodule.work.domain.event;

import java.util.UUID;

/** Raised once by {@code Risk.create()}. Corresponds to {@code risk.created}. */
public record RiskCreated(UUID riskId) implements RiskDomainEvent {
}
