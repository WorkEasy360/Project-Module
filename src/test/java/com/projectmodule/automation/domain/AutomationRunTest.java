package com.projectmodule.automation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutomationRunTest {

    @Test
    @DisplayName("succeeded() creates a SUCCEEDED run with no error message")
    void succeededCreatesRun() {
        UUID automationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        AutomationRun run = AutomationRun.succeeded(automationId, projectId, "task.overdue");

        assertThat(run.getAutomationId()).isEqualTo(automationId);
        assertThat(run.getProjectId()).isEqualTo(projectId);
        assertThat(run.getTriggerEvent()).isEqualTo("task.overdue");
        assertThat(run.getStatus()).isEqualTo(AutomationRunStatus.SUCCEEDED);
        assertThat(run.getErrorMessage()).isEmpty();
        assertThat(run.getExecutedAt()).isNotNull();
        assertThat(run.isNew()).isTrue();
    }

    @Test
    @DisplayName("failed() creates a FAILED run with the error message")
    void failedCreatesRun() {
        AutomationRun run = AutomationRun.failed(
                UUID.randomUUID(), UUID.randomUUID(), "task.overdue", "Notification port unavailable");

        assertThat(run.getStatus()).isEqualTo(AutomationRunStatus.FAILED);
        assertThat(run.getErrorMessage()).contains("Notification port unavailable");
    }
}
