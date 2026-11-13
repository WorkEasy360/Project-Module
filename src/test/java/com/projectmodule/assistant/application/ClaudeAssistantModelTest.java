package com.projectmodule.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClaudeAssistantModelTest {

    @Test
    @DisplayName("a key pasted with spaces, a line break, quotes or a header name still works")
    void cleansPastedKeys() {
        assertThat(ClaudeAssistantModel.cleanKey("  sk-ant-abc123\n")).isEqualTo("sk-ant-abc123");
        assertThat(ClaudeAssistantModel.cleanKey("x-api-key: sk-ant-abc123")).isEqualTo("sk-ant-abc123");
        assertThat(ClaudeAssistantModel.cleanKey("\"sk-ant-abc123\"")).isEqualTo("sk-ant-abc123");
        assertThat(ClaudeAssistantModel.cleanKey("Bearer sk-ant-abc123")).isEqualTo("sk-ant-abc123");
        assertThat(ClaudeAssistantModel.cleanKey("--header \"x-api-key: sk-ant-usr-abc_123-XYZ\" \\")).isEqualTo("sk-ant-usr-abc_123-XYZ");
        assertThat(ClaudeAssistantModel.cleanKey("other-provider-key\r\n")).isEqualTo("other-provider-key");
        assertThat(ClaudeAssistantModel.cleanKey("   ")).isEmpty();
        assertThat(ClaudeAssistantModel.cleanKey(null)).isEmpty();
    }

    @Test
    @DisplayName("no key leaves the assistant off")
    void noKeyIsOff() {
        assertThat(new ClaudeAssistantModel(" \n", "claude-opus-5-5").isEnabled()).isFalse();
        assertThat(new ClaudeAssistantModel("sk-ant-x", "claude-opus-5-5").isEnabled()).isTrue();
    }
}
