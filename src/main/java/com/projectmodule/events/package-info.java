/**
 * Domain event publishing: envelope, publisher and transactional outbox.
 *
 * <p>Events are raised by aggregates and published after the transaction commits, so a
 * rollback can never leave an event delivered for a change that did not happen. The outbox
 * row is written in the same transaction as the state change, which keeps delivery correct
 * while no external message bus exists yet.
 *
 * <p>The event catalogue is defined in {@code docs/project/05-EVENTS.md}.
 */
package com.projectmodule.events;
