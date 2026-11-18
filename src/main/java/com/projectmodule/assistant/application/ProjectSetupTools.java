package com.projectmodule.assistant.application;

import static com.projectmodule.assistant.application.AssistantTools.choice;
import static com.projectmodule.assistant.application.AssistantTools.date;
import static com.projectmodule.assistant.application.AssistantTools.id;
import static com.projectmodule.assistant.application.AssistantTools.optional;
import static com.projectmodule.assistant.application.AssistantTools.optionalDate;
import static com.projectmodule.assistant.application.AssistantTools.optionalUuid;
import static com.projectmodule.assistant.application.AssistantTools.priority;
import static com.projectmodule.assistant.application.AssistantTools.required;
import static com.projectmodule.assistant.application.AssistantTools.role;
import static com.projectmodule.assistant.application.AssistantTools.text;
import static com.projectmodule.assistant.application.AssistantTools.tool;
import static com.projectmodule.assistant.application.AssistantTools.uuid;
import static com.projectmodule.assistant.application.AssistantTools.word;

import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectmodule.account.application.CreatedInvitation;
import com.projectmodule.account.application.WorkspaceApplicationService;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ProjectModuleException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.project.api.dto.AddMemberRequest;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.ProjectMemberApplicationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateChecklistRequest;
import com.projectmodule.work.api.dto.CreateTaskRequest;
import com.projectmodule.work.api.dto.UpdateChecklistRequest;
import com.projectmodule.work.application.ChecklistApplicationService;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.domain.Checklist;
import com.projectmodule.work.domain.Task;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The assistant's tools for setting up work: a whole project in one approved step
 * ({@code set_up_project}), checklist items and invitations. Like {@link AssistantTools}, every
 * call goes through the same application services as the screens, as the person, so their
 * permissions apply unchanged.
 */
@Component
public class ProjectSetupTools {

    static final Set<String> WRITE_TOOLS = Set.of("set_up_project", "add_to_task", "update_checklist_item",
            "invite_to_workspace");

    static final int MAX_PLAN_TASKS = 60;
    static final int MAX_PLAN_ITEMS = 30;
    static final int MAX_PER_TASK = 25;

    private static final List<String> PRIORITIES = Arrays.stream(ProjectPriority.values()).map(Enum::name).toList();

    private final ProjectApplicationService projects;
    private final ProjectMemberApplicationService members;
    private final TaskApplicationService tasks;
    private final ChecklistApplicationService checklists;
    private final WorkspaceApplicationService workspace;
    private final ObjectMapper objectMapper;

    public ProjectSetupTools(ProjectApplicationService projects, ProjectMemberApplicationService members,
                             TaskApplicationService tasks, ChecklistApplicationService checklists,
                             WorkspaceApplicationService workspace, ObjectMapper objectMapper) {
        this.projects = projects;
        this.members = members;
        this.tasks = tasks;
        this.checklists = checklists;
        this.workspace = workspace;
        this.objectMapper = objectMapper;
    }

    boolean handles(String name) {
        return WRITE_TOOLS.contains(name);
    }

    // ------------------------------------------------------------------ definitions

    List<Tool> definitions() {
        Map<String, Object> taskItem = object(props(
                "name", text("What needs to be done"),
                "description", text("More detail"),
                "assignee_id", id("Who does it; must be the person, a current project member, or someone in this plan's members"),
                "priority", choice("LOW, MEDIUM (shown as Normal) or HIGH; defaults to MEDIUM", PRIORITIES),
                "due_date", date("When it is due"),
                "tags", strings("Short labels, such as design or urgent"),
                "checklist", strings("Checklist items to tick off")), List.of("name"));
        return List.of(
                tool("set_up_project",
                        "Set up a whole project in one approved step: either create a new project (give name) or add to an "
                                + "existing one (give project_id). Can add members and tasks, each task with checklist items. "
                                + "Use this for any job with more than a couple of changes. Needs the person's approval.",
                        props("project_id", id("An existing project to add to; leave out to create a new project"),
                                "name", text("Name of the new project"),
                                "description", text("What the new project is for"),
                                "start_date", date("New project's start"),
                                "target_end_date", date("New project's due date"),
                                "customer_contact", text("Who the new project is for: a customer, client or contact"),
                                "project_tags", strings("Short labels for the new project, such as Website or Marketing"),
                                "members", array("People to add to the project", object(props(
                                        "user_id", id("The person, from list_people"),
                                        "role", choice("MEMBER works on tasks, MANAGER plans, VIEWER reads", List.of("MANAGER", "MEMBER", "VIEWER"))),
                                        List.of("user_id", "role"))),
                                "tasks", array("Tasks to create", taskItem)),
                        List.of()),
                tool("add_to_task", "Add checklist items to an existing task. Needs the person's approval.",
                        props("task_id", id("The task's id"), "checklist", strings("Checklist items")),
                        List.of("task_id", "checklist")),
                tool("update_checklist_item", "Tick or untick a checklist item, or change its text. Needs the person's approval.",
                        props("task_id", id("The task the item is on"), "item_id", id("The item, from get_task"),
                                "text", text("New text"), "checked", bool("true when ticked")),
                        List.of("task_id", "item_id")),
                tool("invite_to_workspace", "Invite someone to the workspace by email (workspace admins only). Returns a join link. Needs the person's approval.",
                        props("email", text("Their email address")), List.of("email")));
    }

    // ------------------------------------------------------------------ approval text

    String describe(RequestContext context, String name, Map<String, Object> input) {
        return switch (name) {
            case "set_up_project" -> new Plan(context, input).describe();
            case "add_to_task" -> {
                Task task = tasks.getTask(context, uuid(input, "task_id"));
                List<String> items = limited(strings(input, "checklist"), MAX_PER_TASK, "checklist items");
                if (items.isEmpty()) {
                    throw new ValidationException("Give at least one checklist item");
                }
                yield "On \"" + task.getName() + "\" add checklist items: " + String.join(", ", items);
            }
            case "update_checklist_item" -> {
                Checklist item = findChecklistItem(context, input);
                List<String> changes = new ArrayList<>();
                optional(input, "text").ifPresent(v -> changes.add("change it to \"" + v + "\""));
                optionalBool(input, "checked").ifPresent(v -> changes.add(v ? "tick it" : "untick it"));
                yield "Checklist item \"" + item.getText() + "\": " + joinChanges(changes);
            }
            case "invite_to_workspace" -> "Invite " + email(input) + " to the workspace";
            default -> throw new ValidationException("Unknown change: " + name);
        };
    }

    // ------------------------------------------------------------------ execution

    ToolOutcome run(RequestContext context, String name, Map<String, Object> input) {
        return switch (name) {
            case "set_up_project" -> new Plan(context, input).execute();
            case "add_to_task" -> {
                UUID taskId = uuid(input, "task_id");
                List<String> failures = new ArrayList<>();
                int items = 0;
                for (String s : limited(strings(input, "checklist"), MAX_PER_TASK, "checklist items")) {
                    if (attempt(failures, "checklist item \"" + s + "\"",
                            () -> checklists.createChecklist(context, taskId, new CreateChecklistRequest(s))) != null) {
                        items++;
                    }
                }
                yield json(result("checklist_items_added", items, "failed", failures));
            }
            case "update_checklist_item" -> {
                Checklist item = findChecklistItem(context, input);
                Checklist updated = checklists.updateChecklist(context, item.getId(), new UpdateChecklistRequest(
                        optional(input, "text").orElse(null), optionalBool(input, "checked").orElse(null),
                        item.getVersion()));
                yield json(result("item", updated.getText(), "checked", updated.isChecked()));
            }
            case "invite_to_workspace" -> {
                CreatedInvitation created = workspace.invite(context, email(input));
                yield json(result("invited", email(input), "join_link_path", "/#/join/" + created.token(),
                        "note", "Give the person the join link: the site address followed by join_link_path. "
                                + "It is also emailed to them when email is set up."));
            }
            default -> ToolOutcome.failed("There is no tool called " + name);
        };
    }

    /** The task's checklist, added to get_task. */
    Map<String, Object> taskBreakdown(RequestContext context, UUID taskId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("checklist", checklists.listChecklists(context, taskId).stream()
                .map(c -> result("id", c.getId(), "text", c.getText(), "checked", c.isChecked())).toList());
        return out;
    }

    // ------------------------------------------------------------------ a whole plan

    /** One set_up_project call: checked in full before the person is asked, then built in order. */
    private final class Plan {
        private final RequestContext context;
        private final Optional<UUID> existingProjectId;
        private final Optional<Project> existing;
        private final Map<String, Object> input;
        private final List<Map<String, Object>> memberItems;
        private final List<Map<String, Object>> taskItems;
        private final Map<UUID, String> people = new HashMap<>();

        Plan(RequestContext context, Map<String, Object> input) {
            this.context = context;
            this.input = input;
            this.existingProjectId = optionalUuid(input, "project_id");
            this.existing = existingProjectId.map(id -> projects.getProject(context, id));
            this.memberItems = limited(objects(input, "members"), MAX_PLAN_ITEMS, "members");
            this.taskItems = limited(objects(input, "tasks"), MAX_PLAN_TASKS, "tasks");
            for (UserAccount person : workspace.listPeople(context)) {
                people.put(person.getId(), person.getDisplayName());
            }
            validate();
        }

        private void validate() {
            if (existing.isEmpty() && optional(input, "name").isEmpty()) {
                throw new ValidationException("Give a name for a new project, or a project_id to add to");
            }
            if (existing.isPresent() && memberItems.isEmpty() && taskItems.isEmpty()) {
                throw new ValidationException("Nothing to add was given");
            }
            optionalDate(input, "start_date");
            optionalDate(input, "target_end_date");

            Set<UUID> onProject = new HashSet<>();
            onProject.add(context.requireUserId().value());
            existingProjectId.ifPresent(id -> members.listMembers(context, id)
                    .forEach(m -> onProject.add(m.getUserId().value())));
            for (Map<String, Object> m : memberItems) {
                UUID user = uuid(m, "user_id");
                if (!people.containsKey(user)) {
                    throw new ValidationException("user_id " + user + " is not in this workspace; use list_people");
                }
                role(required(m, "role"));
                onProject.add(user);
            }

            Set<String> taskNames = new HashSet<>();
            for (Map<String, Object> t : taskItems) {
                String name = required(t, "name");
                if (!taskNames.add(key(name))) {
                    throw new ValidationException("Two tasks are called \"" + name + "\"; give them different names");
                }
                optionalUuid(t, "assignee_id").ifPresent(a -> {
                    if (!people.containsKey(a)) {
                        throw new ValidationException("Task \"" + name + "\": the assignee is not in this workspace; use list_people");
                    }
                    if (!onProject.contains(a)) {
                        throw new ValidationException("Task \"" + name + "\": " + people.get(a)
                                + " is not on the project; add them in members first");
                    }
                });
                optional(t, "priority").ifPresent(AssistantTools::priority);
                optionalDate(t, "due_date");
                limited(strings(t, "checklist"), MAX_PER_TASK, "checklist items on \"" + name + "\"");
            }
        }

        String describe() {
            List<String> lines = new ArrayList<>();
            if (existing.isPresent()) {
                lines.add("Add to project \"" + existing.get().getName() + "\":");
            } else {
                StringBuilder s = new StringBuilder("Create project \"" + required(input, "name") + "\"");
                optional(input, "customer_contact").ifPresent(c -> s.append(" for ").append(c));
                optionalDate(input, "start_date").ifPresent(d -> s.append(", starting ").append(d));
                optionalDate(input, "target_end_date").ifPresent(d -> s.append(", due ").append(d));
                lines.add(s + ":");
            }
            if (!memberItems.isEmpty()) {
                lines.add("• Team: " + String.join(", ", memberItems.stream()
                        .map(m -> people.get(uuid(m, "user_id")) + " (" + word(required(m, "role")) + ")").toList()));
            }
            if (!taskItems.isEmpty()) {
                lines.add("• " + taskItems.size() + (taskItems.size() == 1 ? " task:" : " tasks:"));
                for (Map<String, Object> t : taskItems) {
                    List<String> bits = new ArrayList<>();
                    optionalUuid(t, "assignee_id").ifPresent(a -> bits.add(people.get(a)));
                    optional(t, "priority").ifPresent(p -> bits.add(word(priority(p).name())));
                    optionalDate(t, "due_date").ifPresent(d -> bits.add("due " + d));
                    int items = strings(t, "checklist").size();
                    if (items > 0) {
                        bits.add(items + (items == 1 ? " checklist item" : " checklist items"));
                    }
                    lines.add("   – " + required(t, "name") + (bits.isEmpty() ? "" : ": " + String.join(", ", bits)));
                }
            }
            return String.join("\n", lines);
        }

        ToolOutcome execute() {
            List<String> failures = new ArrayList<>();
            UUID projectId;
            String projectName;
            if (existing.isPresent()) {
                projectId = existing.get().getId();
                projectName = existing.get().getName();
            } else {
                Project created = projects.createProject(context, new CreateProjectRequest(required(input, "name"),
                        optional(input, "description").orElse(null), ProjectPriority.MEDIUM,
                        optionalDate(input, "start_date").orElse(null), optionalDate(input, "target_end_date").orElse(null),
                        optional(input, "customer_contact").orElse(null),
                        strings(input, "project_tags").isEmpty() ? null : strings(input, "project_tags")));
                projectId = created.getId();
                projectName = created.getName();
            }

            int added = 0;
            for (Map<String, Object> m : memberItems) {
                UUID user = uuid(m, "user_id");
                if (attempt(failures, "add " + people.get(user), () -> members.addMember(context, projectId,
                        new AddMemberRequest(user, role(required(m, "role"))))) != null) {
                    added++;
                }
            }

            Map<String, UUID> taskIds = new LinkedHashMap<>();
            int itemCount = 0;
            for (Map<String, Object> t : taskItems) {
                String taskName = required(t, "name");
                Task task = attempt(failures, "task \"" + taskName + "\"", () -> tasks.createTask(context, projectId,
                        new CreateTaskRequest(taskName, optional(t, "description").orElse(null),
                                optionalDate(t, "due_date").orElse(null), optionalUuid(t, "assignee_id").orElse(null),
                                null,
                                optional(t, "priority").map(AssistantTools::priority).orElse(null),
                                null,
                                strings(t, "tags").isEmpty() ? null : strings(t, "tags"))));
                if (task == null) {
                    continue;
                }
                taskIds.put(taskName, task.getId());
                for (String s : strings(t, "checklist")) {
                    if (attempt(failures, "checklist item \"" + s + "\"",
                            () -> checklists.createChecklist(context, task.getId(), new CreateChecklistRequest(s))) != null) {
                        itemCount++;
                    }
                }
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("project_id", projectId);
            out.put("project", projectName);
            out.put("project_created", existing.isEmpty());
            out.put("members_added", added);
            out.put("tasks_created", taskIds.size());
            out.put("task_ids", taskIds.entrySet().stream()
                    .map(e -> result("name", e.getKey(), "id", e.getValue())).toList());
            out.put("checklist_items_created", itemCount);
            out.put("failed", failures);
            return json(out);
        }
    }

    /** Runs one step of a larger change; a refusal is recorded and the rest carries on. */
    private static <T> T attempt(List<String> failures, String what, Supplier<T> step) {
        try {
            return step.get();
        } catch (ProjectModuleException e) {
            failures.add(what + ": " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ lookups

    private Checklist findChecklistItem(RequestContext context, Map<String, Object> input) {
        UUID id = uuid(input, "item_id");
        return checklists.listChecklists(context, uuid(input, "task_id")).stream().filter(c -> c.getId().equals(id))
                .findFirst().orElseThrow(() -> new ValidationException("No such checklist item on that task"));
    }

    // ------------------------------------------------------------------ reading the model's input

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static String email(Map<String, Object> input) {
        String email = required(input, "email");
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ValidationException("email must be an email address");
        }
        return email;
    }

    private static String joinChanges(List<String> changes) {
        if (changes.isEmpty()) {
            throw new ValidationException("Nothing to change was given");
        }
        return String.join(", ", changes);
    }

    private static <T> List<T> limited(List<T> items, int max, String what) {
        if (items.size() > max) {
            throw new ValidationException("At most " + max + " " + what + " at once; split the job");
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> objects(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ValidationException(key + " must be a list");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new ValidationException(key + " must be a list of objects");
            }
            out.add((Map<String, Object>) map);
        }
        return out;
    }

    static List<String> strings(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new ValidationException(key + " must be a list of text");
        }
        return list.stream().filter(v -> v != null && !v.toString().isBlank()).map(v -> v.toString().trim()).toList();
    }

    static Optional<Integer> optionalInt(Map<String, Object> input, String key) {
        return optional(input, key).map(v -> {
            try {
                return Integer.parseInt(v.replaceAll("\\.0+$", ""));
            } catch (NumberFormatException e) {
                throw new ValidationException(key + " must be a whole number");
            }
        });
    }

    static Optional<Boolean> optionalBool(Map<String, Object> input, String key) {
        return optional(input, key).map(v -> switch (v.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new ValidationException(key + " must be true or false");
        });
    }

    private ToolOutcome json(Object value) {
        try {
            return ToolOutcome.ok(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write a tool result", e);
        }
    }

    /** An ordered map from alternating keys and values. */
    static Map<String, Object> result(Object... keysAndValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }

    // ------------------------------------------------------------------ schema helpers

    /** Schema properties in a fixed order, so the tool definitions (and the prompt cache) stay stable. */
    static Map<String, Object> props(Object... keysAndValues) {
        return result(keysAndValues);
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "object");
        out.put("properties", properties);
        out.put("required", required);
        return out;
    }

    private static Map<String, Object> array(String description, Map<String, Object> items) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "array");
        out.put("description", description);
        out.put("items", items);
        return out;
    }

    private static Map<String, Object> strings(String description) {
        return array(description, Map.of("type", "string"));
    }

    private static Map<String, Object> bool(String description) {
        return Map.of("type", "boolean", "description", description);
    }
}
