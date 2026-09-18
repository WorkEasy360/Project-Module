package com.projectmodule.work.domain;

/**
 * The value type declared for a {@link ProjectCustomField}, per
 * {@code docs/project/11-CUSTOMFIELD-SPEC.md} (approved) §4/Decision #3 — the minimal four-type
 * system: {@code TEXT}, {@code NUMBER}, {@code DATE}, {@code BOOLEAN}. Immutable once set on a
 * field (Decision #5): changing it out from under an existing value could silently invalidate
 * that value, so retyping a field means archiving it and creating a new one.
 */
public enum CustomFieldValueType {

    /** Any non-blank string. No further constraint. */
    TEXT,

    /** Must parse as a number. */
    NUMBER,

    /** Must parse as an ISO-8601 date ({@code yyyy-MM-dd}). */
    DATE,

    /** Must be exactly {@code true} or {@code false}. */
    BOOLEAN
}
