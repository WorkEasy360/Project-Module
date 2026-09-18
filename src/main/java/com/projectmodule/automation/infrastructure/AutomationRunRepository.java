package com.projectmodule.automation.infrastructure;

import com.projectmodule.automation.domain.AutomationRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for automation execution history. Append-only — no update/delete methods. */
public interface AutomationRunRepository extends JpaRepository<AutomationRun, UUID> {

    List<AutomationRun> findByAutomationIdOrderByExecutedAtDesc(UUID automationId);
}
