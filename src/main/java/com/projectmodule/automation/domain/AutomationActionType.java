package com.projectmodule.automation.domain;

/**
 * What a {@link ProjectAutomation} does when it fires, per
 * {@code docs/project/28-AUTOMATION-SPEC.md} §1/§5 — exactly the two existing, non-mutating
 * integration ports, not a broader mutating-action allowlist (deferred, see
 * {@code docs/project/27-AI-ACTIONS-SPEC.md} §1 for the identical reasoning applied to AI).
 */
public enum AutomationActionType {
    /** Calls the existing {@code NotificationPort}. Requires {@code actionRecipientId}. */
    NOTIFY,
    /** Calls the existing {@code ChatPort}. Requires {@code actionChannelReference}. */
    CHAT_MESSAGE
}
