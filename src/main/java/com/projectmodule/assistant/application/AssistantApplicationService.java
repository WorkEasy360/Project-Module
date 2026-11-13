package com.projectmodule.assistant.application;

import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolChoiceNone;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.assistant.domain.AssistantConversation;
import com.projectmodule.assistant.infrastructure.AssistantConversationRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.IntegrationUnavailableException;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ProjectModuleException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.UuidV7;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Runs a conversation with the assistant: sends it to the model, carries out the read-only tools it
 * asks for, and stops before any change so the person can approve it.
 *
 * <p>The conversation is kept on the server, never sent by the browser, so nobody can hand the model
 * a history, or a tool result, that did not happen. Every tool runs as the caller with their own
 * permissions; approval is an extra step on top of those checks, never a replacement for them.
 *
 * <p>Deliberately not one database transaction: a model reply can take many seconds and must not
 * hold a connection open. Each change a tool makes commits in its own service's transaction.
 */
@Service
public class AssistantApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AssistantApplicationService.class);

    /** Model calls per reply, so a confused loop cannot run up a bill. The last one may not use tools. */
    static final int MAX_MODEL_CALLS = 14;
    /** Past this many messages, the person is asked to start a new conversation. */
    static final int MAX_HISTORY = 200;
    static final int MAX_USER_TEXT = 4000;
    private static final Duration APPROVAL_WINDOW = Duration.ofMinutes(30);
    private static final long MAX_TOKENS = 16_000L;
    static final String THINKING_BINDING_BETA = "thinking-binding-controls-2026-08-01";
    static final String REFUSAL_FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private static final TypeReference<List<MessageParam>> HISTORY_TYPE = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> INPUT_TYPE = new TypeReference<>() { };

    /** One tool call in a reply that is waiting for approval. Read-only calls already carry their result. */
    record PendingCall(String toolUseId, String name, Map<String, Object> input, String summary, boolean awaitsApproval,
                       String result, boolean error, String navigateTo) {
    }

    record PendingAction(String id, Instant expiresAt, List<PendingCall> calls) {
    }

    /** What the person sees after one exchange. */
    public record Reply(UUID conversationId, List<String> messages, List<String> pendingChanges, String navigateTo) {

        public boolean awaitingApproval() {
            return !pendingChanges.isEmpty();
        }
    }

    private final AssistantModel model;
    private final AssistantTools tools;
    private final AssistantConversationRepository conversations;
    private final UserAccountRepository accounts;
    private final ObjectMapper appMapper;
    private final ObjectMapper sdkMapper = ObjectMappers.jsonMapper();
    private final String systemPrompt;
    private final OutputConfig.Effort effort;
    private final boolean refusalFallback;

    public AssistantApplicationService(AssistantModel model,
                                       AssistantTools tools,
                                       AssistantConversationRepository conversations,
                                       UserAccountRepository accounts,
                                       ObjectMapper appMapper,
                                       @Value("${projectmodule.assistant.effort:medium}") String effort,
                                       @Value("${projectmodule.assistant.refusal-fallback:true}") boolean refusalFallback) {
        this.model = model;
        this.tools = tools;
        this.conversations = conversations;
        this.accounts = accounts;
        this.appMapper = appMapper;
        this.systemPrompt = readSystemPrompt();
        this.effort = OutputConfig.Effort.of(effort.toLowerCase(java.util.Locale.ROOT));
        this.refusalFallback = refusalFallback;
    }

    public boolean isEnabled() {
        return model.isEnabled();
    }

    public String modelId() {
        return model.modelId();
    }

    /**
     * Sends the person's message. A change the assistant proposed earlier and the person did not
     * approve counts as declined: they moved on.
     *
     * @param pageContext where the person is in the app and their local date, written by the app
     */
    public Reply chat(RequestContext context, UUID conversationId, String text, String pageContext) {
        requireEnabled();
        String message = text == null ? "" : text.trim();
        if (message.isEmpty()) {
            throw new ValidationException("Type a message");
        }
        if (message.length() > MAX_USER_TEXT) {
            throw new ValidationException("Messages can be at most " + MAX_USER_TEXT + " characters");
        }
        AssistantConversation conversation = conversationId == null
                ? AssistantConversation.start(context.requireUserId().value(), organizationOf(context))
                : owned(context, conversationId);
        List<MessageParam> history = readHistory(conversation);
        if (history.size() >= MAX_HISTORY) {
            throw new BusinessRuleViolationException("This conversation is long. Start a new one to keep going.");
        }

        List<ContentBlockParam> userBlocks = new ArrayList<>();
        readPending(conversation).ifPresent(pending -> userBlocks.addAll(results(context, pending, false)));
        conversation.setPendingAction(null);
        if (pageContext != null && !pageContext.isBlank()) {
            userBlocks.add(text("[The app says: " + pageContext.trim() + "]"));
        }
        userBlocks.add(text(message));
        history.add(MessageParam.ofUserOfContentBlockParams(userBlocks));
        return run(context, conversation, history);
    }

    /** Carries out, or declines, the changes the assistant is waiting on, then lets it continue. */
    public Reply decide(RequestContext context, UUID conversationId, boolean approve) {
        requireEnabled();
        AssistantConversation conversation = owned(context, conversationId);
        PendingAction pending = readPending(conversation)
                .orElseThrow(() -> new BusinessRuleViolationException("There is nothing waiting for approval"));
        conversation.setPendingAction(null);
        if (pending.expiresAt().isBefore(Instant.now())) {
            approve = false;
        }
        List<MessageParam> history = readHistory(conversation);
        history.add(MessageParam.ofUserOfContentBlockParams(results(context, pending, approve)));
        return run(context, conversation, history);
    }

    // ------------------------------------------------------------------ the loop

    private Reply run(RequestContext context, AssistantConversation conversation, List<MessageParam> history) {
        List<String> said = new ArrayList<>();
        String navigateTo = null;

        for (int call = 1; call <= MAX_MODEL_CALLS; call++) {
            Message response = send(context, history, call == MAX_MODEL_CALLS);
            history.add(response.toParam());
            for (ContentBlock block : response.content()) {
                block.text().map(t -> t.text().trim()).filter(t -> !t.isEmpty()).ifPresent(said::add);
            }

            StopReason stop = response.stopReason().orElse(StopReason.END_TURN);
            if (StopReason.REFUSAL.equals(stop)) {
                said.add("I can't help with that request.");
                break;
            }
            if (StopReason.MAX_TOKENS.equals(stop) || StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED.equals(stop)) {
                said.add("My answer was cut short. Could you ask in a narrower way?");
                break;
            }
            if (!StopReason.TOOL_USE.equals(stop)) {
                break;
            }

            List<PendingCall> calls = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                block.toolUse().ifPresent(use -> calls.add(prepare(context, use)));
            }
            for (PendingCall c : calls) {
                if (c.navigateTo() != null) {
                    navigateTo = c.navigateTo();
                }
            }
            if (calls.stream().anyMatch(PendingCall::awaitsApproval)) {
                PendingAction pending = new PendingAction(UuidV7.generate().toString(),
                        Instant.now().plus(APPROVAL_WINDOW), calls);
                conversation.setPendingAction(write(appMapper, pending));
                save(conversation, history);
                return new Reply(conversation.getId(), said,
                        calls.stream().filter(PendingCall::awaitsApproval).map(PendingCall::summary).toList(), navigateTo);
            }
            history.add(MessageParam.ofUserOfContentBlockParams(calls.stream().map(AssistantApplicationService::result).toList()));
        }

        save(conversation, history);
        if (said.isEmpty()) {
            said.add("Done.");
        }
        return new Reply(conversation.getId(), said, List.of(), navigateTo);
    }

    /** A read-only call runs now; a change is checked and described, and waits for approval. */
    private PendingCall prepare(RequestContext context, ToolUseBlock use) {
        Map<String, Object> input = input(use);
        if (tools.isWrite(use.name())) {
            try {
                String summary = tools.describe(context, use.name(), input);
                return new PendingCall(use.id(), use.name(), input, summary, true, null, false, null);
            } catch (ProjectModuleException e) {
                // Not possible as asked: the model hears why, and the person is not asked to approve it.
                return new PendingCall(use.id(), use.name(), input, null, false, e.getMessage(), true, null);
            }
        }
        ToolOutcome outcome = tools.execute(context, use.name(), input);
        return new PendingCall(use.id(), use.name(), input, null, false, outcome.content(), outcome.error(),
                outcome.navigateTo());
    }

    /** The tool results for a reply that waited: approved changes run now, as the caller. */
    private List<ContentBlockParam> results(RequestContext context, PendingAction pending, boolean approve) {
        List<ContentBlockParam> out = new ArrayList<>();
        for (PendingCall c : pending.calls()) {
            if (!c.awaitsApproval()) {
                out.add(result(c));
            } else if (approve) {
                ToolOutcome outcome = tools.execute(context, c.name(), c.input());
                out.add(toolResult(c.toolUseId(), outcome.content(), outcome.error()));
            } else {
                out.add(toolResult(c.toolUseId(), "The person declined this change, so it was not made.", false));
            }
        }
        return out;
    }

    private Message send(RequestContext context, List<MessageParam> history, boolean lastCall) {
        try {
            return sendOnce(context, history, lastCall, true);
        } catch (BadRequestException e) {
            if (!rejectsOptionalFeatures(e)) {
                throw failure(e);
            }
            // The optional beta features are not available to this API key: answer without them.
            log.warn("assistant: optional API features refused, retrying without them");
            try {
                return sendOnce(context, history, lastCall, false);
            } catch (AnthropicException retry) {
                throw failure(retry);
            }
        } catch (AnthropicException e) {
            throw failure(e);
        }
    }

    private static boolean rejectsOptionalFeatures(BadRequestException e) {
        String detail = String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT);
        return detail.contains("anthropic-beta") || detail.contains("block_binding") || detail.contains("fallbacks")
                || detail.contains("extra inputs are not permitted") || detail.contains("beta");
    }

    /** What the person is told, by cause, so an administrator can act on it. */
    private IntegrationUnavailableException failure(AnthropicException e) {
        if (e instanceof RateLimitException) {
            return IntegrationUnavailableException.withMessage("The assistant is busy right now. Try again in a minute.", e);
        }
        if (e instanceof AnthropicServiceException service) {
            int status = service.statusCode();
            String detail = String.valueOf(e.getMessage());
            log.warn("assistant model call failed: status {}: {}", status, detail.length() > 300 ? detail.substring(0, 300) : detail);
            String lower = detail.toLowerCase(java.util.Locale.ROOT);
            String message;
            if (status == 401) {
                message = "The assistant's API key was not accepted. An administrator needs to check ANTHROPIC_API_KEY on the server.";
            } else if (status == 403) {
                message = "The assistant's API key is not allowed to do this. An administrator needs to check the Anthropic account.";
            } else if (lower.contains("credit balance") || lower.contains("billing")) {
                message = "The assistant's Anthropic account has no credit left. An administrator needs to add credit.";
            } else if (status == 404) {
                message = "The assistant's model is not available to this API key. An administrator needs to check the model setting.";
            } else if (status >= 500) {
                message = "The assistant's service is having trouble right now. Try again in a minute.";
            } else {
                message = "The assistant could not answer right now (error " + status + ").";
            }
            return IntegrationUnavailableException.withMessage(message, e);
        }
        log.warn("assistant model call failed: {}", e.getClass().getSimpleName());
        return IntegrationUnavailableException.withMessage("The assistant could not be reached.", e);
    }

    private Message sendOnce(RequestContext context, List<MessageParam> history, boolean lastCall, boolean optionalFeatures) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model.modelId())
                .maxTokens(MAX_TOKENS)
                // The fixed instructions and tools come first and are cached; who is asking follows them.
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder().text(systemPrompt)
                                .cacheControl(CacheControlEphemeral.builder().build()).build(),
                        TextBlockParam.builder().text(aboutCaller(context)).build()))
                .messages(history)
                .outputConfig(OutputConfig.builder().effort(effort).build());
        tools.definitions().forEach(params::addTool);
        if (lastCall) {
            params.toolChoice(ToolChoiceNone.builder().build());
        }
        // A conversation saved before the instructions or tools changed (a new release) carries
        // thinking blocks bound to the old ones. Without this the API rejects every later message in
        // it; with it, the API drops just those stale blocks and the conversation carries on.
        if (optionalFeatures) {
            List<String> betas = new ArrayList<>(List.of(THINKING_BINDING_BETA));
            params.putAdditionalBodyProperty("thinking", JsonValue.from(Map.of(
                    "type", "adaptive",
                    "block_binding", Map.of("prefix_mismatch_behavior", "drop_block"))));
            if (refusalFallback) {
                // If a safety classifier declines, the API retries on a suitable model instead of stopping.
                betas.add(REFUSAL_FALLBACK_BETA);
                params.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
            }
            params.putAdditionalHeader("anthropic-beta", String.join(",", betas));
        }
        Message response = model.send(params.build());
        log.debug("assistant reply: stop={} input={} cached={} output={}", response.stopReason().orElse(null),
                response.usage().inputTokens(), response.usage().cacheReadInputTokens().orElse(0L),
                response.usage().outputTokens());
        return response;
    }

    private String aboutCaller(RequestContext context) {
        UserAccount me = accounts.findById(context.requireUserId().value())
                .orElseThrow(() -> new MissingIdentityException("Your session is no longer valid"));
        return "You are helping " + me.getDisplayName() + ", a workspace " + me.getWorkspaceRole().name().toLowerCase()
                + ". Today's date on the server is " + LocalDate.now() + "; prefer the local date the app gives.";
    }

    // ------------------------------------------------------------------ storage

    private AssistantConversation owned(RequestContext context, UUID conversationId) {
        return conversations.findById(conversationId)
                .filter(c -> c.belongsTo(context.requireUserId().value(), organizationOf(context)))
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
    }

    private List<MessageParam> readHistory(AssistantConversation conversation) {
        try {
            return new ArrayList<>(sdkMapper.readValue(conversation.getMessages(), HISTORY_TYPE));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read conversation " + conversation.getId(), e);
        }
    }

    private java.util.Optional<PendingAction> readPending(AssistantConversation conversation) {
        return conversation.getPendingAction().map(json -> {
            try {
                return appMapper.readValue(json, PendingAction.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Could not read the pending change", e);
            }
        });
    }

    private void save(AssistantConversation conversation, List<MessageParam> history) {
        conversation.replaceMessages(write(sdkMapper, history));
        conversations.save(conversation);
    }

    private static String write(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not save the conversation", e);
        }
    }

    // ------------------------------------------------------------------ small pieces

    private void requireEnabled() {
        if (!model.isEnabled()) {
            throw IntegrationUnavailableException.withMessage(
                    "The assistant is not set up yet. An administrator needs to add an Anthropic API key.", null);
        }
    }

    private static UUID organizationOf(RequestContext context) {
        return context.organizationId()
                .orElseThrow(() -> new MissingIdentityException("No organization was supplied with this request"))
                .value();
    }

    private static Map<String, Object> input(ToolUseBlock use) {
        try {
            Map<String, Object> input = use._input().convert(INPUT_TYPE);
            return input == null ? Map.of() : input;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private static ContentBlockParam result(PendingCall c) {
        return toolResult(c.toolUseId(), c.result(), c.error());
    }

    private static ContentBlockParam toolResult(String toolUseId, String content, boolean error) {
        return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                .toolUseId(toolUseId)
                .content(content == null ? "" : content)
                .isError(error)
                .build());
    }

    private static ContentBlockParam text(String value) {
        return ContentBlockParam.ofText(TextBlockParam.builder().text(value).build());
    }

    private static String readSystemPrompt() {
        try (InputStream in = new ClassPathResource("assistant/system-prompt.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("The assistant's instructions are missing", e);
        }
    }
}
