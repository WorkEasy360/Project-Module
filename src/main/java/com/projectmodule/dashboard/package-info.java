/**
 * Dashboard read model: an organization-wide summary computed from existing Project data.
 *
 * <p>This is a read model only — it owns no entity, no table and no migration. It reads the
 * existing {@code projects} table through {@code ProjectRepository} and reshapes the result for
 * display; it never changes Project state and holds no business rule. See
 * {@code docs/project/08-ROADMAP.md} ("Basic dashboard", P0).
 */
package com.projectmodule.dashboard;
