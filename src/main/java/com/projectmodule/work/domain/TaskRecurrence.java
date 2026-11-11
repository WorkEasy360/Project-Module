package com.projectmodule.work.domain;

import java.time.LocalDate;

/** How often a task repeats. Completing a repeating task creates its next occurrence. */
public enum TaskRecurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY;

    LocalDate next(LocalDate date) {
        return switch (this) {
            case NONE -> date;
            case DAILY -> date.plusDays(1);
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
        };
    }
}
