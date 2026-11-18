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
    @DisplayName("the catalogue has the 8 project and task event types, excluding AI/Automation")
    void catalogueHasExactlyEightValues() {
        assertThat(AutomationTriggerEvent.values()).hasSize(8);
    }

    @Test
    @DisplayName("fromEventType() round-trips eventType()")
    void fromEventTypeRoundTrips() {
        assertThat(AutomationTriggerEvent.fromEventType("task.blocked"))
                .contains(AutomationTriggerEvent.TASK_BLOCKED);
        assertThat(AutomationTriggerEvent.fromEventType("unknown")).isEmpty();
    }
}
