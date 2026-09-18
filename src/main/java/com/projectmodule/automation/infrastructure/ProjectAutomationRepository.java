package com.projectmodule.automation.infrastructure;

import com.projectmodule.automation.domain.ProjectAutomation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for automation rules. Archived rules are excluded explicitly, not by a global filter. */
public interface ProjectAutomationRepository extends JpaRepository<ProjectAutomation, UUID> {

    Optional<ProjectAutomation> findByIdAndArchivedAtIsNull(UUID id);

    List<ProjectAutomation> findByProjectIdAndArchivedAtIsNullOrderByCreatedAtAsc(UUID projectId);

    /**
     * Candidate rules for {@code AutomationDispatcher} to evaluate for one trigger — matches
     * {@code enabled}/non-archived filtering the dispatcher would otherwise redo in memory
     * (see {@code docs/project/28-AUTOMATION-SPEC.md} §6/§10).
     */
    List<ProjectAutomation> findByProjectIdAndTriggerEventAndEnabledIsTrueAndArchivedAtIsNull(
            UUID projectId, String triggerEvent);
}
