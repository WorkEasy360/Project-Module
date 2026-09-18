package com.projectmodule.automation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutomationTriggerEventTest {

    @Test
    @DisplayName("isValid() is true for every event type in the catalogue")
    void isValidTrueForCatalogueValues() {
        for (AutomationTriggerEvent value : AutomationTriggerEvent.values()) {
            assertThat(AutomationTriggerEvent.isValid(value.eventType())).isTrue();
        }
    }

    @Test
    @DisplayName("isValid() is false for an arbitrary string, including a well-formed but unknown event type")
    void isValidFalseForArbitraryStrings() {
        assertThat(AutomationTriggerEvent.isValid("task.deleted")).isFalse();
        assertThat(AutomationTriggerEvent.isValid("not-an-event")).isFalse();
        assertThat(AutomationTriggerEvent.isValid("")).isFalse();
    }

    @Test
    @DisplayName("isValid() is false for the AI/Automation catalogue sections, deliberately excluded per §2")
    void isValidFalseForAiAndAutomationEvents() {
        assertThat(AutomationTriggerEvent.isValid("ai.recommendation.created")).isFalse();
        assertThat(AutomationTriggerEvent.isValid("ai.action.created")).isFalse();
        assertThat(AutomationTriggerEvent.isValid("automation.created")).isFalse();
        assertThat(AutomationTriggerEvent.isValid("automation.executed")).isFalse();
        assertThat(AutomationTriggerEvent.isValid("automation.failed")).isFalse();
    }

    @Test
    @DisplayName("the catalogue has exactly the 19 event types docs/project/05-EVENTS.md lists, excluding AI/Automation")
    void catalogueHasExactlyNineteenValues() {
        assertThat(AutomationTriggerEvent.values()).hasSize(19);
    }

    @Test
    @DisplayName("fromEventType() round-trips eventType()")
    void fromEventTypeRoundTrips() {
        assertThat(AutomationTriggerEvent.fromEventType("task.overdue"))
                .contains(AutomationTriggerEvent.TASK_OVERDUE);
        assertThat(AutomationTriggerEvent.fromEventType("unknown")).isEmpty();
    }
}
