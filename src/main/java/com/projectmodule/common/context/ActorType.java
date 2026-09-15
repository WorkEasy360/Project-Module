package com.projectmodule.common.context;

/**
 * Who or what initiated a request.
 *
 * <p>Recorded on every audited change so a human action, an AI action and an automation run
 * are never indistinguishable in the activity trail.
 *
 * <p>Only {@link #HUMAN} is ever derived from an HTTP request. AI and automation contexts are
 * constructed internally, never taken from a caller-supplied header, so a client cannot claim
 * to be the AI in order to obtain different treatment.
 */
public enum ActorType {

    /** A person acting through the API. */
    HUMAN,

    /** An AI tool invocation, always subject to the same authorization and validation. */
    AI,

    /** An automation rule execution. */
    AUTOMATION,

    /** The module itself, for scheduled or internal maintenance work. */
    SYSTEM
}
