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
        String key = cleanKey(apiKey);
        this.client = key.isEmpty() ? null : AnthropicOkHttpClient.builder()
                .apiKey(key)
                // A chat reply should not keep someone waiting for minutes; the SDK still retries.
                .timeout(Duration.ofSeconds(90))
                .build();
    }

    /**
     * The key as people paste it into a settings screen: possibly with spaces, a line break,
     * quotes, or the header name copied from a curl example ("x-api-key: sk-ant-...").
     */
    static String cleanKey(String raw) {
        if (raw == null) {
            return "";
        }
        // A whole curl line or header pasted in: take the key itself.
        java.util.regex.Matcher anthropicKey = java.util.regex.Pattern.compile("sk-ant-[A-Za-z0-9_-]+").matcher(raw);
        if (anthropicKey.find()) {
            return anthropicKey.group();
        }
        String key = raw.strip();
        key = key.replaceFirst("(?i)^(x-api-key\\s*:|authorization\\s*:\\s*bearer|bearer)\\s*", "");
        key = key.replaceAll("^[\"']+|[\"']+$", "");
        return key.replaceAll("\\s+", "");
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
