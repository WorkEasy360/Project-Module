/**
 * Bounded context: project automation. ProjectAutomation rules and AutomationRun execution history.
 *
 * <p>Layering within this package follows the module architecture:
 * {@code api} -> {@code application} -> {@code domain}, with {@code infrastructure}
 * providing persistence and outbound adapters. Subpackages are added as features land.
 */
package com.projectmodule.automation;
