/**
 * Integration layer: outbound ports and adapters for modules this one does not own.
 *
 * <p>The Project Module references User, Team, Organization, Document, Calendar,
 * Notification, Chat and Authentication but never implements or stores them. External
 * identities are held as opaque identifiers with no foreign key, because the owning tables
 * live in another module. Organization and Authentication are handled by the existing
 * {@code RequestContext} boundary rather than a port here; Team has no separate integration
 * point in {@code docs/project/02-ARCHITECTURE.md}.
 *
 * <p>{@code port} holds the five outbound contracts named in that architecture diagram:
 * {@code UserDirectoryPort}, {@code NotificationPort}, {@code CalendarPort},
 * {@code DocumentPort}, {@code ChatPort}. {@code adapter} holds their implementations — today,
 * one honestly-named no-op/logging adapter per port, since none of those five modules exist yet.
 * An adapter logs what it was asked to do and returns an empty or void result; it never
 * fabricates a response as if a real module had answered. See
 * {@code docs/project/06-INTEGRATION.md}.
 *
 * <p>Not yet wired into any existing controller or service — this layer is the contract only.
 */
package com.projectmodule.integration;
