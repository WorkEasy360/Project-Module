package com.projectmodule.assistant.application;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Claude, through the official Anthropic SDK. Turned on by setting {@code ANTHROPIC_API_KEY}; the
 * key is read by the SDK and never stored or logged by this application.
 */
@Component
public class ClaudeAssistantModel implements AssistantModel {

    private final AnthropicClient client;
    private final String modelId;

    public ClaudeAssistantModel(@Value("${ANTHROPIC_API_KEY:}") String apiKey,
                                @Value("${projectmodule.assistant.model:claude-opus-5-5}") String modelId) {
        this.modelId = modelId;
        this.client = apiKey.isBlank() ? null : AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                // A chat reply should not keep someone waiting for minutes; the SDK still retries.
                .timeout(Duration.ofSeconds(90))
                .build();
    }

    @Override
    public boolean isEnabled() {
        return client != null;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public Message send(MessageCreateParams params) {
        return client.messages().create(params);
    }
}
