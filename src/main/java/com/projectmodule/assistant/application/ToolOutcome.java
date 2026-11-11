package com.projectmodule.assistant.application;

/**
 * What one tool call produced: the text the model reads, whether it failed, and, for navigation,
 * the page the app should open.
 */
public record ToolOutcome(String content, boolean error, String navigateTo) {

    public static ToolOutcome ok(String content) {
        return new ToolOutcome(content, false, null);
    }

    public static ToolOutcome failed(String message) {
        return new ToolOutcome(message, true, null);
    }

    public static ToolOutcome navigate(String route, String content) {
        return new ToolOutcome(content, false, route);
    }
}
