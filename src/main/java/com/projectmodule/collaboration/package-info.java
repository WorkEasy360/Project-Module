/**
 * Bounded context: collaboration and audit. ProjectComment and the append-only ProjectActivity trail.
 *
 * <p>Layering within this package follows the module architecture:
 * {@code api} -> {@code application} -> {@code domain}, with {@code infrastructure}
 * providing persistence and outbound adapters. Subpackages are added as features land.
 */
package com.projectmodule.collaboration;
