package com.projectmodule.assistant.application;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;

/** The language model behind the assistant. An interface so tests can script its replies. */
public interface AssistantModel {

    /** Whether a model is configured. Without one the assistant reports itself unavailable. */
    boolean isEnabled();

    /** The model id requests should name. */
    String modelId();

    Message send(MessageCreateParams params);
}
