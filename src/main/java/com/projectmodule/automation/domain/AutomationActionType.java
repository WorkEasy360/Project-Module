package com.projectmodule.automation.domain;

/** What an automation rule does when it fires. */
public enum AutomationActionType {
    /** Sends an in-app notification through {@code NotificationPort}. Requires {@code actionRecipientId}. */
    NOTIFY
}
