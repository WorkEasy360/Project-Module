package com.projectmodule.assistant.application;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.account.application.WorkspaceApplicationService;
import com.projectmodule.collaboration.domain.ProjectActivity;
import com.projectmodule.collaboration.infrastructure.ProjectActivityRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ProjectModuleException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.notification.application.NotificationService;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.ProjectMemberApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.api.dto.AddMemberRequest;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.project.domain.ProjectRole;
import com.projectmodule.timetracking.application.TimeTrackingApplicationService;
import com.projectmodule.timetracking.domain.TimeEntry;
import com.projectmodule.work.api.dto.CreateCommentRequest;
import com.projectmodule.work.api.dto.CreateTaskRequest;
import com.projectmodule.work.api.dto.UpdateTaskRequest;
import com.projectmodule.work.application.GlobalSearchApplicationService;
import com.projectmodule.work.application.ProjectCommentApplicationService;
import com.projectmodule.work.application.ProjectHealthApplicationService;
import com.projectmodule.work.application.ReportsApplicationService;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.domain.ProjectComment;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskSortField;
import com.projectmodule.work.domain.TaskStatus;
import com.projectmodule.work.domain.TaskRecurrence;
import com.projectmodule.common.api.SortDirection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Everything the assistant can do, each as a tool that calls the same application services the
 * screens use. A tool runs as the person in the conversation, so the existing workspace and
 * permission checks apply unchanged: the assistant can never do more than that person could.
 *
 * <p>Tools that change data are listed in {@link #WRITE_TOOLS}. They are never run when the model
 * asks: the conversation pauses, the person sees {@link #describe} and approves or declines.
 */
@Component
public class AssistantTools {

    static final Set<String> WRITE_TOOLS =
            Set.of("create_task", "update_task", "add_comment", "log_time", "create_project", "add_project_member");

    private static final int LIST_LIMIT = 50;
    private static final List<String> STATUSES = Arrays.stream(TaskStatus.values()).map(Enum::name).toList();
    private static final List<String> RECURRENCES = Arrays.stream(TaskRecurrence.values()).map(Enum::name).toList();
    private static final List<String> PRIORITIES = Arrays.stream(ProjectPriority.values()).map(Enum::name).toList();
    /** OWNER is set when a project is created and cannot be given to anyone else here. */
    private static final List<String> ADDABLE_ROLES = List.of("MANAGER", "MEMBER", "VIEWER");

    /** The pages the assistant may open: page name to route, with {project} and {task} filled in. */
    private static final Map<String, String> PAGES = new LinkedHashMap<>();

    static {
        PAGES.put("home", "/dashboard");
        PAGES.put("projects", "/projects");
        PAGES.put("my_tasks", "/tasks");
        PAGES.put("calendar", "/calendar");
        PAGES.put("team", "/team");
        PAGES.put("timesheet", "/timesheet");
        PAGES.put("reports", "/reports");
        PAGES.put("templates", "/templates");
        PAGES.put("automations", "/automations");
        PAGES.put("settings", "/settings");
        PAGES.put("project_overview", "/projects/{project}/overview");
        PAGES.put("project_tasks", "/projects/{project}/tasks");
        PAGES.put("project_board", "/projects/{project}/kanban");
        PAGES.put("project_calendar", "/projects/{project}/calendar");
        PAGES.put("project_team", "/projects/{project}/members");
        PAGES.put("project_timeline", "/projects/{project}/timeline");
        PAGES.put("project_gantt", "/projects/{project}/gantt");
        PAGES.put("project_activity", "/projects/{project}/activity");
        PAGES.put("project_reports", "/projects/{project}/reports");
        PAGES.put("project_risks", "/projects/{project}/risks");
        PAGES.put("project_issues", "/projects/{project}/issues");
        PAGES.put("project_decisions", "/projects/{project}/decisions");
        PAGES.put("project_phases", "/projects/{project}/phases");
        PAGES.put("project_milestones", "/projects/{project}/milestones");
        PAGES.put("project_task_lists", "/projects/{project}/task-lists");
        PAGES.put("project_task_relationships", "/projects/{project}/dependencies");
        PAGES.put("project_health", "/projects/{project}/health");
        PAGES.put("project_delays", "/projects/{project}/delayed");
        PAGES.put("project_discussion", "/projects/{project}/comments");
        PAGES.put("project_custom_fields", "/projects/{project}/custom-fields");
        PAGES.put("project_automation", "/projects/{project}/automations");
        PAGES.put("project_ai_suggestions", "/projects/{project}/ai");
        PAGES.put("task", "/projects/{project}/tasks/{task}");
    }

    private final ProjectApplicationService projects;
    private final ProjectMemberApplicationService members;
    private final ProjectAuthorizationService authorization;
    private final TaskApplicationService tasks;
    private final ProjectHealthApplicationService health;
    private final ReportsApplicationService reports;
    private final ProjectCommentApplicationService comments;
    private final TimeTrackingApplicationService time;
    private final GlobalSearchApplicationService search;
    private final WorkspaceApplicationService workspace;
    private final NotificationService notifications;
    private final ProjectActivityRepository activity;
    private final UserAccountRepository accounts;
    private final ObjectMapper objectMapper;
    private final ProjectSetupTools setup;

    public AssistantTools(ProjectApplicationService projects,
                          ProjectMemberApplicationService members,
                          ProjectAuthorizationService authorization,
                          TaskApplicationService tasks,
                          ProjectHealthApplicationService health,
                          ReportsApplicationService reports,
                          ProjectCommentApplicationService comments,
                          TimeTrackingApplicationService time,
                          GlobalSearchApplicationService search,
                          WorkspaceApplicationService workspace,
                          NotificationService notifications,
                          ProjectActivityRepository activity,
                          UserAccountRepository accounts,
                          ObjectMapper objectMapper,
                          ProjectSetupTools setup) {
        this.projects = projects;
        this.members = members;
        this.authorization = authorization;
        this.tasks = tasks;
        this.health = health;
        this.reports = reports;
        this.comments = comments;
        this.time = time;
        this.search = search;
        this.workspace = workspace;
        this.notifications = notifications;
        this.activity = activity;
        this.accounts = accounts;
        this.objectMapper = objectMapper;
        this.setup = setup;
    }

    // ------------------------------------------------------------------ definitions

    /** In a fixed order, so the request prefix stays identical and the prompt cache keeps working. */
    public List<Tool> definitions() {
        List<Tool> all = new ArrayList<>(coreDefinitions());
        all.addAll(setup.definitions());
        return List.copyOf(all);
    }

    private List<Tool> coreDefinitions() {
        return List.of(
                tool("list_projects", "List the projects the person belongs to, most recently updated first.",
                        Map.of(), List.of()),
                tool("get_project", "Details of one project: status, dates, health and task counts by status.",
                        Map.of("project_id", id("The project's id")), List.of("project_id")),
                tool("list_tasks", "List a project's tasks, optionally filtered by status or assignee.",
                        Map.of("project_id", id("The project's id"),
                                "status", choice("Only tasks with this status", STATUSES),
                                "assignee_id", id("Only tasks assigned to this person")),
                        List.of("project_id")),
                tool("get_task", "Everything about one task: details, subtasks, checklist, what it waits for, recent comments and time logged.",
                        Map.of("task_id", id("The task's id")), List.of("task_id")),
                tool("my_tasks", "Open tasks assigned to the person, across all their projects.",
                        Map.of("include_completed", Map.of("type", "boolean",
                                "description", "Also include completed tasks")), List.of()),
                tool("search", "Find projects and tasks by name, across the person's projects.",
                        Map.of("query", text("At least two characters")), List.of("query")),
                tool("list_people", "Everyone in the person's workspace, with their ids. Use it to turn a name into an id.",
                        Map.of(), List.of()),
                tool("list_project_members", "Who is on a project, and with which role.",
                        Map.of("project_id", id("The project's id")), List.of("project_id")),
                tool("project_activity", "Recent changes on a project: who did what, newest first.",
                        Map.of("project_id", id("The project's id")), List.of("project_id")),
                tool("my_timesheet", "Time the person logged between two dates (at most 62 days apart).",
                        Map.of("from", date("First day"), "to", date("Last day")), List.of("from", "to")),
                tool("my_notifications", "The person's latest notifications.", Map.of(), List.of()),
                tool("navigate", "Open a page of the app for the person. Pages starting project_ need project_id; task needs project_id and task_id.",
                        Map.of("page", choice("The page to open", List.copyOf(PAGES.keySet())),
                                "project_id", id("For project pages and task"),
                                "task_id", id("For the task page")),
                        List.of("page")),
                tool("create_task", "Create one task in a project. For several tasks, or tasks with subtasks or checklists, use set_up_project. Needs the person's approval before it happens.",
                        ProjectSetupTools.props("project_id", id("The project's id"),
                                "name", text("What needs to be done"),
                                "description", text("More detail"),
                                "assignee_id", id("Who it is assigned to; must be on the project"),
                                "priority", choice("Defaults to MEDIUM", PRIORITIES),
                                "start_date", date("When work should start"),
                                "due_date", date("When it is due"),
                                "estimate_minutes", Map.of("type", "integer", "description", "Expected effort in minutes"),
                                "tags", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Short labels"),
                                "recurrence", choice("Repeat the task; defaults to NONE", RECURRENCES)),
                        List.of("project_id", "name")),
                tool("update_task", "Change a task: status, priority, assignee, dates, name, description, estimate, tags or repeat. Needs the person's approval.",
                        ProjectSetupTools.props("task_id", id("The task's id"),
                                "name", text("New name"),
                                "description", text("New description"),
                                "status", choice("New status", STATUSES),
                                "priority", choice("New priority", PRIORITIES),
                                "assignee_id", id("New assignee; must be on the project"),
                                "start_date", date("New start date"),
                                "due_date", date("New due date"),
                                "estimate_minutes", Map.of("type", "integer", "description", "Expected effort in minutes"),
                                "tags", Map.of("type", "array", "items", Map.of("type", "string"), "description", "The full new set of labels"),
                                "recurrence", choice("Repeat the task", RECURRENCES)),
                        List.of("task_id")),
                tool("add_comment", "Comment on a task, as the person. Needs the person's approval.",
                        Map.of("task_id", id("The task's id"), "body", text("The comment")),
                        List.of("task_id", "body")),
                tool("log_time", "Log time the person spent on a task. Needs the person's approval.",
                        Map.of("task_id", id("The task's id"),
                                "minutes", Map.of("type", "integer", "description", "Time spent, in minutes (1 to 1440)"),
                                "work_date", date("The day the work was done"),
                                "note", text("What was done"),
                                "billable", Map.of("type", "boolean", "description", "Whether the time is billable")),
                        List.of("task_id", "minutes", "work_date")),
                tool("create_project", "Create a project; the person becomes its owner. Needs the person's approval.",
                        Map.of("name", text("The project's name"),
                                "description", text("What the project is for"),
                                "priority", choice("Defaults to MEDIUM", PRIORITIES),
                                "start_date", date("When it starts"),
                                "target_end_date", date("When it should finish")),
                        List.of("name")),
                tool("add_project_member", "Add someone from the workspace to a project. Needs the person's approval.",
                        Map.of("project_id", id("The project's id"),
                                "user_id", id("The person, from list_people"),
                                "role", choice("Their role on the project; MEMBER can work on tasks", ADDABLE_ROLES)),
                        List.of("project_id", "user_id", "role")));
    }

    public boolean isWrite(String name) {
        return WRITE_TOOLS.contains(name) || ProjectSetupTools.WRITE_TOOLS.contains(name);
    }

    // ------------------------------------------------------------------ approval text

    /**
     * What a change will do, in words the person approves. Looking things up here also checks the
     * person can see them, so a change they could never make is refused before they are asked.
     *
     * @throws ProjectModuleException or {@link ValidationException} if the change is not possible
     */
    public String describe(RequestContext context, String name, Map<String, Object> input) {
        switch (name) {
            case "create_task" -> {
                Project project = projects.getProject(context, uuid(input, "project_id"));
                StringBuilder s = new StringBuilder("Create task \"" + required(input, "name") + "\" in " + project.getName());
                optionalUuid(input, "assignee_id").ifPresent(id -> s.append(", assigned to ").append(nameOf(id)));
                optional(input, "priority").ifPresent(p -> s.append(", ").append(word(p)).append(" priority"));
                optionalDate(input, "start_date").ifPresent(d -> s.append(", starting ").append(d));
                optionalDate(input, "due_date").ifPresent(d -> s.append(", due ").append(d));
                ProjectSetupTools.optionalInt(input, "estimate_minutes").ifPresent(m -> s.append(", estimate ").append(m).append(" min"));
                optional(input, "recurrence").map(ProjectSetupTools::recurrence).filter(r -> r != TaskRecurrence.NONE)
                        .ifPresent(r -> s.append(", repeats ").append(word(r.name())));
                List<String> tags = ProjectSetupTools.strings(input, "tags");
                if (!tags.isEmpty()) {
                    s.append(", tags ").append(String.join(", ", tags));
                }
                return s.toString();
            }
            case "update_task" -> {
                Task task = tasks.getTask(context, uuid(input, "task_id"));
                List<String> changes = new ArrayList<>();
                optional(input, "name").ifPresent(v -> changes.add("rename it to \"" + v + "\""));
                optional(input, "status").ifPresent(v -> changes.add("set status to " + word(v)));
                optional(input, "priority").ifPresent(v -> changes.add("set priority to " + word(v)));
                optionalUuid(input, "assignee_id").ifPresent(v -> changes.add("assign it to " + nameOf(v)));
                optionalDate(input, "start_date").ifPresent(v -> changes.add("set start date to " + v));
                optionalDate(input, "due_date").ifPresent(v -> changes.add("set due date to " + v));
                optional(input, "description").ifPresent(v -> changes.add("update the description"));
                ProjectSetupTools.optionalInt(input, "estimate_minutes").ifPresent(v -> changes.add("set estimate to " + v + " min"));
                if (input.get("tags") != null) {
                    List<String> tags = ProjectSetupTools.strings(input, "tags");
                    changes.add(tags.isEmpty() ? "clear its tags" : "set tags to " + String.join(", ", tags));
                }
                optional(input, "recurrence").map(ProjectSetupTools::recurrence).ifPresent(r ->
                        changes.add(r == TaskRecurrence.NONE ? "stop it repeating" : "repeat it " + word(r.name())));
                if (changes.isEmpty()) {
                    throw new ValidationException("Nothing to change was given");
                }
                return "Update \"" + task.getName() + "\": " + String.join(", ", changes);
            }
            case "add_comment" -> {
                Task task = tasks.getTask(context, uuid(input, "task_id"));
                return "Comment on \"" + task.getName() + "\": \"" + required(input, "body") + "\"";
            }
            case "log_time" -> {
                Task task = tasks.getTask(context, uuid(input, "task_id"));
                int minutes = minutes(input);
                return "Log " + (minutes / 60 > 0 ? minutes / 60 + "h " : "") + minutes % 60 + "m on \"" + task.getName()
                        + "\" for " + date(input, "work_date")
                        + (Boolean.TRUE.equals(input.get("billable")) ? " (billable)" : "");
            }
            case "create_project" -> {
                StringBuilder s = new StringBuilder("Create project \"" + required(input, "name") + "\"");
                optional(input, "priority").ifPresent(p -> s.append(", ").append(word(p)).append(" priority"));
                optionalDate(input, "start_date").ifPresent(d -> s.append(", starting ").append(d));
                optionalDate(input, "target_end_date").ifPresent(d -> s.append(", finishing by ").append(d));
                return s.toString();
            }
            case "add_project_member" -> {
                Project project = projects.getProject(context, uuid(input, "project_id"));
                return "Add " + nameOf(uuid(input, "user_id")) + " to " + project.getName() + " as "
                        + word(role(required(input, "role")).name());
            }
            default -> {
                if (setup.handles(name)) {
                    return setup.describe(context, name, input);
                }
                throw new ValidationException("Unknown change: " + name);
            }
        }
    }

    // ------------------------------------------------------------------ execution

    /** Runs a tool as the person. A refusal from the app comes back as a failed outcome, not an exception. */
    public ToolOutcome execute(RequestContext context, String name, Map<String, Object> input) {
        try {
            return run(context, name, input);
        } catch (ProjectModuleException e) {
            return ToolOutcome.failed(e.getMessage());
        }
    }

    private ToolOutcome run(RequestContext context, String name, Map<String, Object> input) {
        return switch (name) {
            case "list_projects" -> json(projects.listProjects(context, PageRequest.of(0, LIST_LIMIT,
                    Sort.by(Sort.Direction.DESC, "updatedAt"))).map(this::projectSummary).getContent());
            case "get_project" -> {
                UUID id = uuid(input, "project_id");
                Map<String, Object> out = projectSummary(projects.getProject(context, id));
                out.put("health", health.getHealth(context, id));
                out.put("report", reports.getSummary(context, id));
                yield json(out);
            }
            case "list_tasks" -> json(tasks.listTasks(context, uuid(input, "project_id"),
                    optional(input, "status").map(AssistantTools::status).orElse(null),
                    optionalUuid(input, "assignee_id").orElse(null), null, null, false,
                    TaskSortField.DUE_DATE, SortDirection.ASC).stream().limit(LIST_LIMIT).map(this::taskSummary).toList());
            case "get_task" -> {
                Task task = tasks.getTask(context, uuid(input, "task_id"));
                Map<String, Object> out = taskSummary(task);
                out.put("description", task.getDescription().orElse(null));
                out.put("tags", task.getTags());
                out.put("estimate_minutes", task.getEstimateMinutes().orElse(null));
                out.put("recurrence", task.getRecurrence());
                out.putAll(setup.taskBreakdown(context, task.getId()));
                out.put("logged_minutes", time.listForTask(context, task.getId()).stream()
                        .mapToInt(TimeEntry::getMinutes).sum());
                List<ProjectComment> all = comments.listTaskComments(context, task.getId());
                out.put("recent_comments", all.subList(Math.max(0, all.size() - 10), all.size()).stream()
                        .map(c -> Map.of("by", nameOf(c.getAuthorId().value()), "at", c.getCreatedAt().toString(),
                                "text", c.getBody()))
                        .toList());
                yield json(out);
            }
            case "my_tasks" -> {
                UUID me = context.requireUserId().value();
                boolean includeCompleted = Boolean.TRUE.equals(input.get("include_completed"));
                List<Map<String, Object>> mine = new ArrayList<>();
                for (Project project : projects.listProjects(context, PageRequest.of(0, LIST_LIMIT)).getContent()) {
                    for (Task task : tasks.listTasks(context, project.getId(), null, me, null, null, false,
                            TaskSortField.DUE_DATE, SortDirection.ASC)) {
                        if (includeCompleted || task.getStatus() != TaskStatus.COMPLETED) {
                            Map<String, Object> row = taskSummary(task);
                            row.put("project", project.getName());
                            mine.add(row);
                        }
                    }
                }
                yield json(mine.stream().limit(LIST_LIMIT).toList());
            }
            case "search" -> json(search.search(context, required(input, "query")));
            case "list_people" -> json(workspace.listPeople(context).stream()
                    .map(p -> Map.of("id", p.getId(), "name", p.getDisplayName(), "email", p.getEmail(),
                            "workspace_role", p.getWorkspaceRole()))
                    .toList());
            case "list_project_members" -> json(members.listMembers(context, uuid(input, "project_id")).stream()
                    .map(m -> Map.of("id", m.getUserId().value(), "name", nameOf(m.getUserId().value()),
                            "role", m.getRole()))
                    .toList());
            case "project_activity" -> {
                UUID id = uuid(input, "project_id");
                projects.findActiveProjectInCallerOrganization(context, id);
                authorization.requirePermission(context.requireUserId(), id, ProjectPermission.VIEW_PROJECT);
                yield json(activity.findByProjectIdOrderByOccurredAtDesc(id, PageRequest.of(0, 25)).getContent()
                        .stream().map(this::activitySummary).toList());
            }
            case "my_timesheet" -> json(time.listMine(context, date(input, "from"), date(input, "to")).stream()
                    .map(e -> Map.of("date", e.getWorkDate().toString(), "minutes", e.getMinutes(),
                            "task_id", e.getTaskId(), "note", e.getNote().orElse(""), "billable", e.isBillable()))
                    .toList());
            case "my_notifications" -> json(notifications.list(context, PageRequest.of(0, 15)).getContent().stream()
                    .map(n -> Map.of("title", n.getTitle(), "read", n.isRead(), "at", n.getCreatedAt().toString()))
                    .toList());
            case "navigate" -> navigate(context, input);
            case "create_task" -> {
                Task created = tasks.createTask(context, uuid(input, "project_id"), new CreateTaskRequest(
                        required(input, "name"), optional(input, "description").orElse(null),
                        optionalDate(input, "due_date").orElse(null), optionalUuid(input, "assignee_id").orElse(null),
                        optionalDate(input, "start_date").orElse(null),
                        optional(input, "priority").map(AssistantTools::priority).orElse(null),
                        ProjectSetupTools.optionalInt(input, "estimate_minutes").orElse(null),
                        optional(input, "recurrence").map(ProjectSetupTools::recurrence).orElse(null),
                        ProjectSetupTools.strings(input, "tags").isEmpty() ? null : ProjectSetupTools.strings(input, "tags")));
                yield json(Map.of("created", taskSummary(created)));
            }
            case "update_task" -> {
                Task current = tasks.getTask(context, uuid(input, "task_id"));
                Task updated = tasks.updateTask(context, current.getId(), new UpdateTaskRequest(
                        optional(input, "name").orElse(null), optional(input, "description").orElse(null),
                        optionalDate(input, "due_date").orElse(null), optionalUuid(input, "assignee_id").orElse(null),
                        optional(input, "status").map(AssistantTools::status).orElse(null), current.getVersion(),
                        optionalDate(input, "start_date").orElse(null),
                        optional(input, "priority").map(AssistantTools::priority).orElse(null),
                        ProjectSetupTools.optionalInt(input, "estimate_minutes").orElse(null),
                        optional(input, "recurrence").map(ProjectSetupTools::recurrence).orElse(null),
                        input.get("tags") == null ? null : ProjectSetupTools.strings(input, "tags")));
                yield json(Map.of("updated", taskSummary(updated)));
            }
            case "add_comment" -> {
                ProjectComment comment = comments.createTaskComment(context, uuid(input, "task_id"),
                        new CreateCommentRequest(required(input, "body")));
                yield json(Map.of("commented", true, "comment_id", comment.getId()));
            }
            case "log_time" -> {
                TimeEntry entry = time.logTime(context, uuid(input, "task_id"), date(input, "work_date"),
                        minutes(input), optional(input, "note").orElse(null),
                        Boolean.TRUE.equals(input.get("billable")));
                yield json(Map.of("logged_minutes", entry.getMinutes(), "date", entry.getWorkDate().toString()));
            }
            case "create_project" -> {
                Project created = projects.createProject(context, new CreateProjectRequest(required(input, "name"),
                        optional(input, "description").orElse(null),
                        optional(input, "priority").map(AssistantTools::priority).orElse(ProjectPriority.MEDIUM),
                        optionalDate(input, "start_date").orElse(null), optionalDate(input, "target_end_date").orElse(null)));
                yield json(Map.of("created", projectSummary(created)));
            }
            case "add_project_member" -> {
                members.addMember(context, uuid(input, "project_id"),
                        new AddMemberRequest(uuid(input, "user_id"), role(required(input, "role"))));
                yield json(Map.of("added", nameOf(uuid(input, "user_id"))));
            }
            default -> setup.handles(name) ? setup.run(context, name, input)
                    : ToolOutcome.failed("There is no tool called " + name);
        };
    }

    private ToolOutcome navigate(RequestContext context, Map<String, Object> input) {
        String page = required(input, "page");
        String route = PAGES.get(page);
        if (route == null) {
            return ToolOutcome.failed("There is no page called " + page);
        }
        if (route.contains("{project}")) {
            UUID projectId = uuid(input, "project_id");
            // Opening a project the person cannot see would only show them an error.
            projects.getProject(context, projectId);
            route = route.replace("{project}", projectId.toString());
        }
        if (route.contains("{task}")) {
            Task task = tasks.getTask(context, uuid(input, "task_id"));
            route = route.replace("{task}", task.getId().toString());
        }
        return ToolOutcome.navigate(route, "Opened " + page.replace('_', ' ') + ".");
    }

    // ------------------------------------------------------------------ shapes the model reads

    private Map<String, Object> projectSummary(Project project) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", project.getId());
        out.put("name", project.getName());
        out.put("status", project.getStatus());
        out.put("priority", project.getPriority());
        out.put("owner", nameOf(project.getOwnerId().value()));
        project.getStartDate().ifPresent(d -> out.put("start_date", d.toString()));
        project.getTargetEndDate().ifPresent(d -> out.put("target_end_date", d.toString()));
        project.getDescription().ifPresent(d -> out.put("description", d));
        return out;
    }

    private Map<String, Object> taskSummary(Task task) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", task.getId());
        out.put("project_id", task.getProjectId());
        out.put("name", task.getName());
        out.put("status", task.getStatus());
        out.put("priority", task.getPriority());
        out.put("assignee", task.getAssigneeId().map(a -> nameOf(a.value())).orElse("Unassigned"));
        task.getAssigneeId().ifPresent(a -> out.put("assignee_id", a.value()));
        task.getStartDate().ifPresent(d -> out.put("start_date", d.toString()));
        task.getDueDate().ifPresent(d -> out.put("due_date", d.toString()));
        return out;
    }

    private Map<String, Object> activitySummary(ProjectActivity entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("who", entry.getActorId().map(a -> nameOf(a.value())).orElse("System"));
        out.put("what", entry.getSummary());
        out.put("at", entry.getOccurredAt().toString());
        return out;
    }

    private String nameOf(UUID userId) {
        return accounts.findById(userId).map(UserAccount::getDisplayName).orElse("Someone");
    }

    private ToolOutcome json(Object value) {
        try {
            return ToolOutcome.ok(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write a tool result", e);
        }
    }

    // ------------------------------------------------------------------ reading the model's input

    static String required(Map<String, Object> input, String key) {
        return optional(input, key).orElseThrow(() -> new ValidationException(key + " is required"));
    }

    static java.util.Optional<String> optional(Map<String, Object> input, String key) {
        Object value = input.get(key);
        String text = value == null ? "" : value.toString().trim();
        return text.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(text);
    }

    static UUID uuid(Map<String, Object> input, String key) {
        return parseUuid(key, required(input, key));
    }

    static java.util.Optional<UUID> optionalUuid(Map<String, Object> input, String key) {
        return optional(input, key).map(v -> parseUuid(key, v));
    }

    static UUID parseUuid(String key, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(key + " must be an id from a lookup, not a name");
        }
    }

    static LocalDate date(Map<String, Object> input, String key) {
        return parseDate(key, required(input, key));
    }

    static java.util.Optional<LocalDate> optionalDate(Map<String, Object> input, String key) {
        return optional(input, key).map(v -> parseDate(key, v));
    }

    static LocalDate parseDate(String key, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException(key + " must be a date such as 2026-11-20");
        }
    }

    private static int minutes(Map<String, Object> input) {
        try {
            return Integer.parseInt(required(input, "minutes").replaceAll("\\.0+$", ""));
        } catch (NumberFormatException e) {
            throw new ValidationException("minutes must be a whole number");
        }
    }

    static TaskStatus status(String value) {
        try {
            return TaskStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("status must be one of " + STATUSES);
        }
    }

    static ProjectRole role(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (!ADDABLE_ROLES.contains(upper)) {
            throw new ValidationException("role must be one of " + ADDABLE_ROLES);
        }
        return ProjectRole.valueOf(upper);
    }

    static ProjectPriority priority(String value) {
        try {
            return ProjectPriority.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("priority must be one of " + PRIORITIES);
        }
    }

    static String word(String token) {
        String lower = token.toLowerCase(Locale.ROOT).replace('_', ' ');
        return "todo".equals(lower) ? "To Do" : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // ------------------------------------------------------------------ schema helpers

    static Tool tool(String name, String description, Map<String, Object> properties, List<String> required) {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        properties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> props.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue())));
        return Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(required).build())
                .build();
    }

    static Map<String, Object> id(String description) {
        return Map.of("type", "string", "description", description + " (an id returned by another tool)");
    }

    static Map<String, Object> text(String description) {
        return Map.of("type", "string", "description", description);
    }

    static Map<String, Object> date(String description) {
        return Map.of("type", "string", "description", description + ", as YYYY-MM-DD");
    }

    static Map<String, Object> choice(String description, List<String> values) {
        return Map.of("type", "string", "description", description, "enum", values);
    }
}
