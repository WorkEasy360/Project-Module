/**
 * Integration layer: outbound ports and adapters for modules this one does not own.
 *
 * <p>The Project Module references User, Team, Organization, Document, Notification and
 * Authentication but never implements or stores them. External
 * identities are held as opaque identifiers with no foreign key, because the owning tables
 * live in another module. Organization and Authentication are handled by the existing
 * {@code RequestContext} boundary rather than a port here; Team has no separate integration
 * point in {@code docs/project/02-ARCHITECTURE.md}.
 *
 * <p>{@code port} holds the outbound contracts {@code UserDirectoryPort}, {@code NotificationPort},
 * {@code DocumentPort} and {@code EmailPort}. {@code adapter} holds their implementations — mostly
 * one honestly-named no-op/logging adapter per port, since those modules do not exist yet.
 * An adapter logs what it was asked to do and returns an empty or void result; it never
 * fabricates a response as if a real module had answered. See
 * {@code docs/project/06-INTEGRATION.md}.
 *
 */
package com.projectmodule.integration;
