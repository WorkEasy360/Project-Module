/**
 * Bounded context: Project and ProjectMember. Owns project lifecycle, settings, status, priority and membership.
 *
 * <p>Layering within this package follows the module architecture:
 * {@code api} -> {@code application} -> {@code domain}, with {@code infrastructure}
 * providing persistence and outbound adapters. Subpackages are added as features land.
 */
package com.projectmodule.project;
