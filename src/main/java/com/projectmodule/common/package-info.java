/**
 * Shared kernel: identifiers, base types, error contracts, paging and clock abstractions used by every bounded context.
 *
 * <p>Layering within this package follows the module architecture:
 * {@code api} -> {@code application} -> {@code domain}, with {@code infrastructure}
 * providing persistence and outbound adapters. Subpackages are added as features land.
 */
package com.projectmodule.common;
