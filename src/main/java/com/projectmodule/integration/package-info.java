/**
 * Integration layer: outbound ports and adapters for modules this one does not own.
 *
 * <p>The Project Module references User, Team, Organization, Document, Calendar,
 * Notification, Chat and Authentication but never implements or stores them. External
 * identities are held as opaque identifiers with no foreign key, because the owning tables
 * live in another module.
 *
 * <p>Planned ports: {@code UserDirectoryPort}, {@code NotificationPort}, {@code CalendarPort},
 * {@code DocumentPort}, {@code ChatPort}. See {@code docs/project/06-INTEGRATION.md}.
 */
package com.projectmodule.integration;
