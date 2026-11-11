package com.projectmodule.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.assistant.domain.AssistantConversation;
import com.projectmodule.assistant.infrastructure.AssistantConversationRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.IntegrationUnavailableException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The conversation loop, with the model's replies scripted so nothing is sent to the real API. */
@ExtendWith(MockitoExtension.class)
class AssistantApplicationServiceTest {

    @Mock
    private AssistantTools tools;

    @Mock
    private AssistantConversationRepository conversations;

    @Mock
    private UserAccountRepository accounts;

    private final ScriptedModel model = new ScriptedModel();
    private final Map<UUID, AssistantConversation> stored = new HashMap<>();
    private AssistantApplicationService service;
    private RequestContext context;
    private UserAccount me;

    @BeforeEach
    void setUp() {
        service = new AssistantApplicationService(model, tools, conversations, accounts,
                new ObjectMapper().findAndRegisterModules(), "medium", true);
        me = UserAccount.register("me@example.com", "Asha", "hash");
        context = contextOf(me);
        lenient().when(accounts.findById(me.getId())).thenReturn(Optional.of(me));
        lenient().when(tools.definitions()).thenReturn(List.of());
        lenient().when(tools.isWrite(anyString())).thenAnswer(call -> AssistantTools.WRITE_TOOLS.contains(call.<String>getArgument(0)));
        lenient().when(conversations.save(any())).thenAnswer(call -> {
            AssistantConversation c = call.getArgument(0);
            stored.put(c.getId(), c);
            return c;
        });
        lenient().when(conversations.findById(any())).thenAnswer(call -> Optional.ofNullable(stored.get(call.<UUID>getArgument(0))));
    }

    private static RequestContext contextOf(UserAccount account) {
        return new ImmutableRequestContext(Optional.of(ExternalUserId.of(account.getId())),
                Optional.of(OrganizationId.of(account.getOrganizationId())), "corr", ActorType.HUMAN);
    }

    // ------------------------------------------------------------------ scripted replies

    private static String text(String value) {
        return "{\"type\":\"text\",\"text\":\"" + value + "\"}";
    }

    private static String toolUse(String id, String name, String inputJson) {
        return "{\"type\":\"tool_use\",\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"input\":" + inputJson + "}";
    }

    private static Message reply(String stopReason, String... blocks) {
        String json = "{\"id\":\"msg_" + UUID.randomUUID() + "\",\"type\":\"message\",\"role\":\"assistant\","
                + "\"model\":\"claude-opus-5-5\",\"content\":[" + String.join(",", blocks) + "],"
                + "\"stop_reason\":\"" + stopReason + "\",\"stop_sequence\":null,"
                + "\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";
        try {
            return ObjectMappers.jsonMapper().readValue(json, Message.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Replies in order, and remembers every request it was sent. */
    static final class ScriptedModel implements AssistantModel {
        final Deque<Message> replies = new ArrayDeque<>();
        final List<MessageCreateParams> requests = new ArrayList<>();
        boolean enabled = true;
        Message repeat;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public String modelId() {
            return "claude-opus-5-5";
        }

        @Override
        public Message send(MessageCreateParams params) {
            requests.add(params);
            return replies.isEmpty() ? repeat : replies.removeFirst();
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("a plain answer comes back, and the conversation is kept for the next message")
    void answers() {
        model.replies.add(reply("end_turn", text("You have 3 projects.")));

        AssistantApplicationService.Reply result = service.chat(context, null, "How many projects?", "page /dashboard");

        assertThat(result.messages()).containsExactly("You have 3 projects.");
        assertThat(result.awaitingApproval()).isFalse();
        assertThat(stored).containsKey(result.conversationId());
        MessageCreateParams sent = model.requests.get(0);
        assertThat(sent.model().asString()).isEqualTo("claude-opus-5-5");
        assertThat(sent.messages()).hasSize(1);
    }

    @Test
    @DisplayName("a lookup runs straight away, as the person, and the model hears the result")
    void readToolsRunImmediately() {
        when(tools.execute(eq(context), eq("list_projects"), anyMap())).thenReturn(ToolOutcome.ok("[{\"name\":\"Apollo\"}]"));
        model.replies.add(reply("tool_use", text("Let me check."), toolUse("tu_1", "list_projects", "{}")));
        model.replies.add(reply("end_turn", text("You are on Apollo.")));

        AssistantApplicationService.Reply result = service.chat(context, null, "Which projects?", null);

        assertThat(result.messages()).containsExactly("Let me check.", "You are on Apollo.");
        // The second request carries: the question, the tool call, and its result.
        assertThat(model.requests.get(1).messages()).hasSize(3);
    }

    @Test
    @DisplayName("a change is never made when the model asks: it waits for the person, then runs on approval")
    void changesWaitForApproval() {
        when(tools.describe(eq(context), eq("create_task"), anyMap())).thenReturn("Create task \"Write post\" in Apollo");
        when(tools.execute(eq(context), eq("create_task"), anyMap())).thenReturn(ToolOutcome.ok("{\"created\":{}}"));
        model.replies.add(reply("tool_use", toolUse("tu_1", "create_task", "{\"project_id\":\"p\",\"name\":\"Write post\"}")));

        AssistantApplicationService.Reply asked = service.chat(context, null, "Add a task to write the post", null);

        assertThat(asked.pendingChanges()).containsExactly("Create task \"Write post\" in Apollo");
        verify(tools, never()).execute(any(), eq("create_task"), anyMap());

        model.replies.add(reply("end_turn", text("Created it.")));
        AssistantApplicationService.Reply done = service.decide(context, asked.conversationId(), true);

        verify(tools).execute(eq(context), eq("create_task"), anyMap());
        assertThat(done.messages()).containsExactly("Created it.");
        assertThat(done.awaitingApproval()).isFalse();
    }

    @Test
    @DisplayName("declining means the change is not made, and the model is told so")
    void declineMakesNoChange() {
        when(tools.describe(eq(context), eq("log_time"), anyMap())).thenReturn("Log 1h on \"Write post\"");
        model.replies.add(reply("tool_use", toolUse("tu_1", "log_time", "{\"task_id\":\"t\",\"minutes\":60,\"work_date\":\"2026-11-10\"}")));
        AssistantApplicationService.Reply asked = service.chat(context, null, "Log an hour", null);

        model.replies.add(reply("end_turn", text("No problem, I left it.")));
        service.decide(context, asked.conversationId(), false);

        verify(tools, never()).execute(any(), eq("log_time"), anyMap());
        assertThat(model.requests.get(1).messages().get(2).toString()).contains("declined");
    }

    @Test
    @DisplayName("writing a new message instead of approving counts as declining")
    void movingOnDeclines() {
        when(tools.describe(eq(context), eq("add_comment"), anyMap())).thenReturn("Comment on \"Write post\"");
        model.replies.add(reply("tool_use", toolUse("tu_1", "add_comment", "{\"task_id\":\"t\",\"body\":\"hi\"}")));
        AssistantApplicationService.Reply asked = service.chat(context, null, "Comment hi", null);

        model.replies.add(reply("end_turn", text("Sure.")));
        service.chat(context, asked.conversationId(), "Actually, never mind", null);

        verify(tools, never()).execute(any(), eq("add_comment"), anyMap());
        assertThatThrownBy(() -> service.decide(context, asked.conversationId(), true))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("a change the person could not make is refused before they are asked to approve it")
    void impossibleChangeIsNotOffered() {
        when(tools.describe(eq(context), eq("update_task"), anyMap()))
                .thenThrow(new com.projectmodule.common.exception.AuthorizationException("You cannot edit this task"));
        model.replies.add(reply("tool_use", toolUse("tu_1", "update_task", "{\"task_id\":\"t\",\"status\":\"COMPLETED\"}")));
        model.replies.add(reply("end_turn", text("You do not have permission to change that task.")));

        AssistantApplicationService.Reply result = service.chat(context, null, "Mark it done", null);

        assertThat(result.awaitingApproval()).isFalse();
        assertThat(result.messages()).containsExactly("You do not have permission to change that task.");
    }

    @Test
    @DisplayName("someone else's conversation cannot be continued or approved")
    void conversationsArePrivate() {
        model.replies.add(reply("end_turn", text("Hi")));
        UUID mine = service.chat(context, null, "Hi", null).conversationId();
        UserAccount other = UserAccount.register("other@example.com", "Ben", "hash");

        assertThatThrownBy(() -> service.chat(contextOf(other), mine, "Hi", null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.decide(contextOf(other), mine, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("without an API key the assistant says it is not set up, and calls nothing")
    void unavailableWithoutKey() {
        model.enabled = false;

        assertThatThrownBy(() -> service.chat(context, null, "Hi", null))
                .isInstanceOf(IntegrationUnavailableException.class)
                .hasMessageContaining("not set up");
        assertThat(model.requests).isEmpty();
    }

    @Test
    @DisplayName("a declined request is answered politely, never with half an answer")
    void handlesRefusal() {
        model.replies.add(reply("refusal"));

        assertThat(service.chat(context, null, "Something it will not do", null).messages())
                .containsExactly("I can't help with that request.");
    }

    @Test
    @DisplayName("a runaway loop stops: the last call is not allowed to use tools")
    void loopIsCapped() {
        when(tools.execute(any(), eq("search"), anyMap())).thenReturn(ToolOutcome.ok("[]"));
        model.repeat = reply("tool_use", toolUse("tu_x", "search", "{\"query\":\"x\"}"));

        service.chat(context, null, "Find it", null);

        assertThat(model.requests).hasSize(AssistantApplicationService.MAX_MODEL_CALLS);
        assertThat(model.requests.get(model.requests.size() - 1).toolChoice()).isPresent();
        assertThat(model.requests.get(0).toolChoice()).isEmpty();
    }

    @Test
    @DisplayName("navigation is passed to the app, and an empty or over-long message is refused")
    void navigatesAndValidates() {
        when(tools.execute(any(), eq("navigate"), anyMap())).thenReturn(ToolOutcome.navigate("/timesheet", "Opened timesheet."));
        model.replies.add(reply("tool_use", toolUse("tu_1", "navigate", "{\"page\":\"timesheet\"}")));
        model.replies.add(reply("end_turn", text("Here is your timesheet.")));

        assertThat(service.chat(context, null, "Open my timesheet", null).navigateTo()).isEqualTo("/timesheet");
        assertThatThrownBy(() -> service.chat(context, null, "  ", null))
                .isInstanceOf(com.projectmodule.common.exception.ValidationException.class);
        assertThatThrownBy(() -> service.chat(context, null, "x".repeat(4001), null))
                .isInstanceOf(com.projectmodule.common.exception.ValidationException.class);
    }

    @Test
    @DisplayName("every request names the cached instructions, the fallback, and an explicit effort")
    void requestShape() {
        model.replies.add(reply("end_turn", text("Hi")));
        service.chat(context, null, "Hi", null);

        MessageCreateParams sent = model.requests.get(0);
        assertThat(sent.system().orElseThrow().asTextBlockParams().get(0).cacheControl()).isPresent();
        assertThat(sent.system().orElseThrow().asTextBlockParams().get(1).text()).contains("Asha");
        assertThat(sent.outputConfig().orElseThrow().effort()).isPresent();
        assertThat(sent._additionalBodyProperties()).containsKey("fallbacks");
    }

    @Test
    @DisplayName("conversations saved before a release keep working: stale thinking is dropped, not refused")
    void staleThinkingIsDropped() {
        model.replies.add(reply("end_turn", text("Hi")));
        service.chat(context, null, "Hi", null);

        MessageCreateParams sent = model.requests.get(0);
        assertThat(sent._additionalBodyProperties().get("thinking").toString())
                .contains("adaptive").contains("drop_block");
        assertThat(sent._additionalHeaders().values("anthropic-beta")).singleElement().asString()
                .contains(AssistantApplicationService.THINKING_BINDING_BETA)
                .contains(AssistantApplicationService.REFUSAL_FALLBACK_BETA);
    }
}
