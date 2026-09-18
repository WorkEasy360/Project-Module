package com.projectmodule.common.api;

/**
 * Sort direction for a List-view {@code sortDir} query parameter, per
 * {@code docs/project/15-LIST-SPEC.md}. Shared across every entity's filtered list endpoint
 * rather than declared per entity, since direction is not entity-specific.
 */
public enum SortDirection {
    ASC,
    DESC
}
