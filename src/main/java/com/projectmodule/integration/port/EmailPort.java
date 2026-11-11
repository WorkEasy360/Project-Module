package com.projectmodule.integration.port;

/** Sends an email to one address. The app works without it: see {@link #isEnabled()}. */
public interface EmailPort {

    /** Whether a mail server is configured, so a screen can offer what depends on email. */
    boolean isEnabled();

    /** Sends the message, or logs that it could not. Never throws: email must not fail the action. */
    void send(String toAddress, String subject, String textBody);
}
