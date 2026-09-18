package com.projectmodule.integration.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoopCalendarAdapterTest {

    private final NoopCalendarAdapter adapter = new NoopCalendarAdapter();

    @Test
    @DisplayName("accepts a schedule sync request without throwing, syncing nothing")
    void doesNotThrow() {
        assertThatCode(() -> adapter.syncProjectSchedule(
                UUID.randomUUID(), "Apollo", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("accepts null dates without throwing")
    void toleratesNullDates() {
        assertThatCode(() -> adapter.syncProjectSchedule(UUID.randomUUID(), "Apollo", null, null))
                .doesNotThrowAnyException();
    }
}
