package com.projectmodule.work.domain;

/** Fields a Task list may be sorted by, per {@code docs/project/15-LIST-SPEC.md} §2. */
public enum TaskSortField {
    CREATED_AT,
    DUE_DATE,
    NAME,
    STATUS
}
