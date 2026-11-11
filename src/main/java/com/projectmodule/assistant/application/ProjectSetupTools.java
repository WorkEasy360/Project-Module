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
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ProjectModuleException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.customization.api.dto.ApplyTemplateRequest;
import com.projectmodule.customization.application.ProjectTemplateApplicationService;
import com.projectmodule.customization.domain.ProjectTemplate;
import com.projectmodule.project.api.dto.AddMemberRequest;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.ProjectMemberApplicationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateChecklistRequest;
import com.projectmodule.work.api.dto.CreateDecisionRequest;
import com.projectmodule.work.api.dto.CreateDependencyRequest;
import com.projectmodule.work.api.dto.CreateIssueRequest;
import com.projectmodule.work.api.dto.CreateMilestoneRequest;
import com.projectmodule.work.api.dto.CreatePhaseRequest;
import com.projectmodule.work.api.dto.CreateRiskRequest;
import com.projectmodule.work.api.dto.CreateSubtaskRequest;
import com.projectmodule.work.api.dto.CreateTaskListRequest;
import com.projectmodule.work.api.dto.CreateTaskRequest;
import com.projectmodule.work.api.dto.UpdateChecklistRequest;
import com.projectmodule.work.api.dto.UpdateMilestoneRequest;
import com.projectmodule.work.api.dto.UpdateRiskRequest;
import com.projectmodule.work.api.dto.UpdateSubtaskRequest;
import com.projectmodule.work.application.ChecklistApplicationService;
import com.projectmodule.work.application.DecisionApplicationService;
import com.projectmodule.work.application.DependencyApplicationService;
import com.projectmodule.work.application.IssueApplicationService;
import com.projectmodule.work.application.MilestoneApplicationService;
import com.projectmodule.work.application.PhaseApplicationService;
import com.projectmodule.work.application.RiskApplicationService;
import com.projectmodule.work.application.SubtaskApplicationService;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.application.TaskListApplicationService;
import com.projectmodule.work.domain.Checklist;
import com.projectmodule.work.domain.Milestone;
import com.projectmodule.work.domain.MilestoneStatus;
import com.projectmodule.work.domain.Phase;
import com.projectmodule.work.domain.Risk;
import com.projectmodule.work.domain.RiskStatus;
import com.projectmodule.work.domain.Subtask;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskRecurrence;
import java.time.LocalDate;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * The assistant's tools for building out a project's structure: a whole plan in one approved step
 * ({@code set_up_project}), subtasks and checklists, dependencies, milestones, risks, issues,
 * decisions, templates and invitations. Like {@link AssistantTools}, every call goes through the
 * same application services as the screens, as the person, so their permissions apply unchanged.
 */
@Component
public class ProjectSetupTools {

    static final Set<String> WRITE_TOOLS = Set.of("set_up_project", "add_to_task", "update_subtask",
            "update_checklist_item", "add_dependency", "update_milestone", "create_risk", "update_risk",
            "create_issue", "record_decision", "invite_to_workspace", "create_project_from_template", "follow_task");
    static final Set<String> READ_TOOLS = Set.of("project_plan", "list_templates");

    static final int MAX_PLAN_TASKS = 60;
    static final int MAX_PLAN_ITEMS = 30;
    static final int MAX_PER_TASK = 25;

    private static final List<String> PRIORITIES = Arrays.stream(ProjectPriority.values()).map(Enum::name).toList();
    private static final List<String> RECURRENCES = Arrays.stream(TaskRecurrence.values()).map(Enum::name).toList();
    private static final List<String> MILESTONE_STATUSES = Arrays.stream(MilestoneStatus.values()).map(Enum::name).toList();
    private static final List<String> RISK_STATUSES = Arrays.stream(RiskStatus.values()).map(Enum::name).toList();

    private final ProjectApplicationService projects;
    private final ProjectMemberApplicationService members;
    private final TaskApplicationService tasks;
    private final SubtaskApplicationService subtasks;
    private final ChecklistApplicationService checklists;
    private final PhaseApplicationService phases;
    private final MilestoneApplicationService milestones;
    private final TaskListApplicationService taskLists;
    private final DependencyApplicationService dependencies;
    private final RiskApplicationService risks;
    private final IssueApplicationService issues;
    private final DecisionApplicationService decisions;
    private final ProjectTemplateApplicationService templates;
    private final WorkspaceApplicationService workspace;
    private final UserAccountRepository accounts;
    private final ObjectMapper objectMapper;

    public ProjectSetupTools(ProjectApplicationService projects, ProjectMemberApplicationService members,
                             TaskApplicationService tasks, SubtaskApplicationService subtasks,
                             ChecklistApplicationService checklists, PhaseApplicationService phases,
                             MilestoneApplicationService milestones, TaskListApplicationService taskLists,
                             DependencyApplicationService dependencies, RiskApplicationService risks,
                             IssueApplicationService issues, DecisionApplicationService decisions,
                             ProjectTemplateApplicationService templates, WorkspaceApplicationService workspace,
                             UserAccountRepository accounts, ObjectMapper objectMapper) {
        this.projects = projects;
        this.members = members;
        this.tasks = tasks;
        this.subtasks = subtasks;
        this.checklists = checklists;
        this.phases = phases;
        this.milestones = milestones;
        this.taskLists = taskLists;
        this.dependencies = dependencies;
        this.risks = risks;
        this.issues = issues;
        this.decisions = decisions;
        this.templates = templates;
        this.workspace = workspace;
        this.accounts = accounts;
        this.objectMapper = objectMapper;
    }

    boolean handles(String name) {
        return WRITE_TOOLS.contains(name) || READ_TOOLS.contains(name);
    }

    // ------------------------------------------------------------------ definitions

    List<Tool> definitions() {
        Map<String, Object> taskItem = object(props(
                "name", text("What needs to be done"),
                "description", text("More detail"),
                "assignee_id", id("Who does it; must be the person, a current project member, or someone in this plan's members"),
                "priority", choice("Defaults to MEDIUM", PRIORITIES),
                "start_date", date("When work starts"),
                "due_date", date("When it is due"),
                "estimate_minutes", integer("Expected effort in minutes"),
                "tags", strings("Short labels, such as design or urgent"),
                "recurrence", choice("Repeat the task; defaults to NONE", RECURRENCES),
                "subtasks", strings("Smaller steps, each becomes a subtask"),
                "checklist", strings("Checklist items to tick off"),
                "depends_on", strings("Names of other tasks in this plan that must finish first")), List.of("name"));
        return List.of(
                tool("project_plan", "A project's structure: phases, milestones, task lists, open risks, issues and decisions, with their ids.",
                        Map.of("project_id", id("The project's id")), List.of("project_id")),
                tool("list_templates", "Project templates in the workspace, with their ids.", Map.of(), List.of()),
                tool("set_up_project",
                        "Set up a whole project in one approved step: either create a new project (give name) or add to an "
                                + "existing one (give project_id). Can add members, phases, milestones, task lists and tasks, "
                                + "each task with subtasks, checklist items and dependencies on other tasks in the plan. "
                                + "Use this for any job with more than a couple of changes. Needs the person's approval.",
                        props("project_id", id("An existing project to add to; leave out to create a new project"),
                                "name", text("Name of the new project"),
                                "description", text("What the new project is for"),
                                "priority", choice("New project's priority; defaults to MEDIUM", PRIORITIES),
                                "start_date", date("New project's start"),
                                "target_end_date", date("New project's planned finish"),
                                "members", array("People to add to the project", object(props(
                                        "user_id", id("The person, from list_people"),
                                        "role", choice("MEMBER works on tasks, MANAGER plans, VIEWER reads", List.of("MANAGER", "MEMBER", "VIEWER"))),
                                        List.of("user_id", "role"))),
                                "phases", array("Stages of the project, in order", object(props(
                                        "name", text("Phase name"), "description", text("What happens in it"),
                                        "start_date", date("Start"), "end_date", date("End")), List.of("name"))),
                                "milestones", array("Key dates or deliverables", object(props(
                                        "name", text("Milestone name"), "description", text("What it marks"),
                                        "due_date", date("When it is due"),
                                        "phase", text("Name of a phase in this plan or the project it belongs to")), List.of("name"))),
                                "task_lists", array("Groups for organising tasks", object(props(
                                        "name", text("List name"), "description", text("What goes in it"),
                                        "phase", text("Name of a phase it belongs to")), List.of("name"))),
                                "tasks", array("Tasks to create", taskItem)),
                        List.of()),
                tool("add_to_task", "Add subtasks and/or checklist items to an existing task. Needs the person's approval.",
                        props("task_id", id("The task's id"), "subtasks", strings("Subtask names"),
                                "checklist", strings("Checklist items")), List.of("task_id")),
                tool("update_subtask", "Rename a subtask or mark it done or not done. Needs the person's approval.",
                        props("task_id", id("The task the subtask is on"), "subtask_id", id("The subtask, from get_task"),
                                "name", text("New name"), "completed", bool("true when done")),
                        List.of("task_id", "subtask_id")),
                tool("update_checklist_item", "Tick or untick a checklist item, or change its text. Needs the person's approval.",
                        props("task_id", id("The task the item is on"), "item_id", id("The item, from get_task"),
                                "text", text("New text"), "checked", bool("true when ticked")),
                        List.of("task_id", "item_id")),
                tool("add_dependency", "Make a task wait for another task to finish first. Needs the person's approval.",
                        props("task_id", id("The task that waits"), "depends_on_task_id", id("The task that must finish first")),
                        List.of("task_id", "depends_on_task_id")),
                tool("update_milestone", "Change a milestone's status, due date or name. Needs the person's approval.",
                        props("project_id", id("The project"), "milestone_id", id("From project_plan"),
                                "status", choice("New status", MILESTONE_STATUSES), "due_date", date("New due date"),
                                "name", text("New name")),
                        List.of("project_id", "milestone_id")),
                tool("create_risk", "Record a risk on a project. Needs the person's approval.",
                        props("project_id", id("The project"), "name", text("The risk"), "description", text("Detail and plan"),
                                "priority", choice("How serious", PRIORITIES)),
                        List.of("project_id", "name", "priority")),
                tool("update_risk", "Change a risk's status or priority, for example mark it resolved. Needs the person's approval.",
                        props("project_id", id("The project"), "risk_id", id("From project_plan"),
                                "status", choice("New status", RISK_STATUSES), "priority", choice("New priority", PRIORITIES)),
                        List.of("project_id", "risk_id")),
                tool("create_issue", "Log an issue (a problem happening now) on a project. Needs the person's approval.",
                        props("project_id", id("The project"), "name", text("The issue"), "description", text("Detail"),
                                "priority", choice("How serious", PRIORITIES)),
                        List.of("project_id", "name", "priority")),
                tool("record_decision", "Record a decision made on a project. Needs the person's approval.",
                        props("project_id", id("The project"), "name", text("The decision"), "description", text("Why, and what it means"),
                                "decided_by", id("Who decided, from list_people")),
                        List.of("project_id", "name")),
                tool("invite_to_workspace", "Invite someone to the workspace by email (workspace admins only). Returns a join link. Needs the person's approval.",
                        props("email", text("Their email address")), List.of("email")),
                tool("create_project_from_template", "Create a new project from a template. Needs the person's approval.",
                        props("template_id", id("From list_templates"), "name", text("The new project's name"),
                                "description", text("What it is for")),
                        List.of("template_id", "name")),
                tool("follow_task", "Follow or unfollow a task to be notified about its comments and changes. Needs the person's approval.",
                        props("task_id", id("The task"), "following", bool("true to follow, false to stop")),
                        List.of("task_id", "following")));
    }

    // ------------------------------------------------------------------ approval text

    String describe(RequestContext context, String name, Map<String, Object> input) {
        return switch (name) {
            case "set_up_project" -> new Plan(context, input).describe();
            case "add_to_task" -> {
                Task task = tasks.getTask(context, uuid(input, "task_id"));
                List<String> subs = limited(strings(input, "subtasks"), MAX_PER_TASK, "subtasks");
                List<String> items = limited(strings(input, "checklist"), MAX_PER_TASK, "checklist items");
                if (subs.isEmpty() && items.isEmpty()) {
                    throw new ValidationException("Give at least one subtask or checklist item");
                }
                StringBuilder s = new StringBuilder("On \"" + task.getName() + "\" add");
                if (!subs.isEmpty()) {
                    s.append(" subtasks: ").append(String.join(", ", subs));
                }
                if (!items.isEmpty()) {
                    s.append(subs.isEmpty() ? " " : "; and ").append("checklist items: ").append(String.join(", ", items));
                }
                yield s.toString();
            }
            case "update_subtask" -> {
                Subtask subtask = findSubtask(context, input);
                List<String> changes = new ArrayList<>();
                optional(input, "name").ifPresent(v -> changes.add("rename it to \"" + v + "\""));
                optionalBool(input, "completed").ifPresent(v -> changes.add(v ? "mark it done" : "mark it not done"));
                yield "Subtask \"" + subtask.getName() + "\": " + joinChanges(changes);
            }
            case "update_checklist_item" -> {
                Checklist item = findChecklistItem(context, input);
                List<String> changes = new ArrayList<>();
                optional(input, "text").ifPresent(v -> changes.add("change it to \"" + v + "\""));
                optionalBool(input, "checked").ifPresent(v -> changes.add(v ? "tick it" : "untick it"));
                yield "Checklist item \"" + item.getText() + "\": " + joinChanges(changes);
            }
            case "add_dependency" -> {
                Task waiting = tasks.getTask(context, uuid(input, "task_id"));
                Task first = tasks.getTask(context, uuid(input, "depends_on_task_id"));
                yield "Make \"" + waiting.getName() + "\" wait for \"" + first.getName() + "\" to finish";
            }
            case "update_milestone" -> {
                Milestone milestone = findMilestone(context, input);
                List<String> changes = new ArrayList<>();
                optional(input, "name").ifPresent(v -> changes.add("rename it to \"" + v + "\""));
                optional(input, "status").ifPresent(v -> changes.add("set status to " + word(milestoneStatus(v).name())));
                optionalDate(input, "due_date").ifPresent(v -> changes.add("set due date to " + v));
                yield "Milestone \"" + milestone.getName() + "\": " + joinChanges(changes);
            }
            case "create_risk", "create_issue" -> {
                Project project = projects.getProject(context, uuid(input, "project_id"));
                yield (name.equals("create_risk") ? "Record risk \"" : "Log issue \"") + required(input, "name") + "\" on "
                        + project.getName() + ", " + word(priority(required(input, "priority")).name()) + " priority";
            }
            case "update_risk" -> {
                Risk risk = findRisk(context, input);
                List<String> changes = new ArrayList<>();
                optional(input, "status").ifPresent(v -> changes.add("set status to " + word(riskStatus(v).name())));
                optional(input, "priority").ifPresent(v -> changes.add("set priority to " + word(priority(v).name())));
                yield "Risk \"" + risk.getName() + "\": " + joinChanges(changes);
            }
            case "record_decision" -> {
                Project project = projects.getProject(context, uuid(input, "project_id"));
                StringBuilder s = new StringBuilder("Record decision \"" + required(input, "name") + "\" on " + project.getName());
                optionalUuid(input, "decided_by").ifPresent(u -> s.append(", decided by ").append(personName(context, u)));
                yield s.toString();
            }
            case "invite_to_workspace" -> "Invite " + email(input) + " to the workspace";
            case "create_project_from_template" -> {
                ProjectTemplate template = templates.getTemplate(context, uuid(input, "template_id"));
                yield "Create project \"" + required(input, "name") + "\" from the template \"" + template.getName() + "\"";
            }
            case "follow_task" -> {
                Task task = tasks.getTask(context, uuid(input, "task_id"));
                yield (Boolean.TRUE.equals(bool(input, "following")) ? "Follow \"" : "Stop following \"") + task.getName() + "\"";
            }
            default -> throw new ValidationException("Unknown change: " + name);
        };
    }

    // ------------------------------------------------------------------ execution

    ToolOutcome run(RequestContext context, String name, Map<String, Object> input) {
        return switch (name) {
            case "project_plan" -> projectPlan(context, uuid(input, "project_id"));
            case "list_templates" -> json(templates.listTemplates(context, PageRequest.of(0, 50)).getContent().stream()
                    .map(t -> {
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("id", t.getId());
                        out.put("name", t.getName());
                        t.getDescription().ifPresent(d -> out.put("description", d));
                        return out;
                    }).toList());
            case "set_up_project" -> new Plan(context, input).execute();
            case "add_to_task" -> {
                UUID taskId = uuid(input, "task_id");
                List<String> failures = new ArrayList<>();
                int subs = 0;
                int items = 0;
                for (String s : limited(strings(input, "subtasks"), MAX_PER_TASK, "subtasks")) {
                    if (attempt(failures, "subtask \"" + s + "\"",
                            () -> subtasks.createSubtask(context, taskId, new CreateSubtaskRequest(s, null))) != null) {
                        subs++;
                    }
                }
                for (String s : limited(strings(input, "checklist"), MAX_PER_TASK, "checklist items")) {
                    if (attempt(failures, "checklist item \"" + s + "\"",
                            () -> checklists.createChecklist(context, taskId, new CreateChecklistRequest(s, null))) != null) {
                        items++;
                    }
                }
                yield json(result("subtasks_added", subs, "checklist_items_added", items, "failed", failures));
            }
            case "update_subtask" -> {
                Subtask subtask = findSubtask(context, input);
                Subtask updated = subtasks.updateSubtask(context, subtask.getId(), new UpdateSubtaskRequest(
                        optional(input, "name").orElse(null), null, optionalBool(input, "completed").orElse(null),
                        subtask.getVersion()));
                yield json(result("subtask", updated.getName(), "completed", updated.isCompleted()));
            }
            case "update_checklist_item" -> {
                Checklist item = findChecklistItem(context, input);
                Checklist updated = checklists.updateChecklist(context, item.getId(), new UpdateChecklistRequest(
                        optional(input, "text").orElse(null), optionalBool(input, "checked").orElse(null),
                        item.getSubtaskId().orElse(null), item.getVersion()));
                yield json(result("item", updated.getText(), "checked", updated.isChecked()));
            }
            case "add_dependency" -> {
                dependencies.createDependency(context, uuid(input, "task_id"),
                        new CreateDependencyRequest(uuid(input, "depends_on_task_id")));
                yield json(result("dependency_added", true));
            }
            case "update_milestone" -> {
                Milestone milestone = findMilestone(context, input);
                Milestone updated = milestones.updateMilestone(context, milestone.getId(), new UpdateMilestoneRequest(
                        optional(input, "name").orElse(null), null, optionalDate(input, "due_date").orElse(null),
                        milestone.getPhaseId().orElse(null),
                        optional(input, "status").map(ProjectSetupTools::milestoneStatus).orElse(null),
                        milestone.getVersion()));
                yield json(milestoneSummary(updated));
            }
            case "create_risk" -> json(result("risk_id", risks.createRisk(context, uuid(input, "project_id"),
                    new CreateRiskRequest(required(input, "name"), optional(input, "description").orElse(null),
                            priority(required(input, "priority")))).getId()));
            case "update_risk" -> {
                Risk risk = findRisk(context, input);
                Risk updated = risks.updateRisk(context, risk.getId(), new UpdateRiskRequest(null, null,
                        optional(input, "priority").map(AssistantTools::priority).orElse(null),
                        optional(input, "status").map(ProjectSetupTools::riskStatus).orElse(null), risk.getVersion()));
                yield json(result("risk", updated.getName(), "status", updated.getStatus(), "priority", updated.getPriority()));
            }
            case "create_issue" -> json(result("issue_id", issues.createIssue(context, uuid(input, "project_id"),
                    new CreateIssueRequest(required(input, "name"), optional(input, "description").orElse(null),
                            priority(required(input, "priority")))).getId()));
            case "record_decision" -> json(result("decision_id", decisions.createDecision(context, uuid(input, "project_id"),
                    new CreateDecisionRequest(required(input, "name"), optional(input, "description").orElse(null),
                            optionalUuid(input, "decided_by").orElse(null))).getId()));
            case "invite_to_workspace" -> {
                CreatedInvitation created = workspace.invite(context, email(input));
                yield json(result("invited", email(input), "join_link_path", "/#/join/" + created.token(),
                        "note", "Give the person the join link: the site address followed by join_link_path. "
                                + "It is also emailed to them when email is set up."));
            }
            case "create_project_from_template" -> {
                Project created = templates.applyTemplate(context, uuid(input, "template_id"),
                        new ApplyTemplateRequest(required(input, "name"), optional(input, "description").orElse(null)));
                yield json(result("created_project_id", created.getId(), "name", created.getName()));
            }
            case "follow_task" -> {
                boolean following = Boolean.TRUE.equals(bool(input, "following"));
                tasks.setFollowing(context, uuid(input, "task_id"), following);
                yield json(result("following", following));
            }
            default -> ToolOutcome.failed("There is no tool called " + name);
        };
    }

    /** Subtasks, checklist and what the task waits for, added to get_task. */
    Map<String, Object> taskBreakdown(RequestContext context, UUID taskId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subtasks", subtasks.listSubtasks(context, taskId).stream()
                .map(s -> result("id", s.getId(), "name", s.getName(), "completed", s.isCompleted())).toList());
        out.put("checklist", checklists.listChecklists(context, taskId).stream()
                .map(c -> result("id", c.getId(), "text", c.getText(), "checked", c.isChecked())).toList());
        out.put("waits_for", dependencies.listDependencies(context, taskId).stream()
                .map(d -> {
                    String prerequisite;
                    try {
                        Task first = tasks.getTask(context, d.getPrerequisiteTaskId());
                        prerequisite = first.getName() + " (" + word(first.getStatus().name()) + ")";
                    } catch (ProjectModuleException e) {
                        prerequisite = "a task you cannot see";
                    }
                    return result("task_id", d.getPrerequisiteTaskId(), "task", prerequisite);
                }).toList());
        return out;
    }

    private ToolOutcome projectPlan(RequestContext context, UUID projectId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("phases", phases.listPhases(context, projectId).stream().map(p -> {
            Map<String, Object> m = result("id", p.getId(), "name", p.getName());
            p.getStartDate().ifPresent(d -> m.put("start_date", d.toString()));
            p.getEndDate().ifPresent(d -> m.put("end_date", d.toString()));
            return m;
        }).toList());
        out.put("milestones", milestones.listMilestones(context, projectId).stream().map(this::milestoneSummary).toList());
        out.put("task_lists", taskLists.listTaskLists(context, projectId).stream()
                .map(l -> result("id", l.getId(), "name", l.getName())).toList());
        out.put("risks", risks.listRisks(context, projectId).stream()
                .map(r -> result("id", r.getId(), "name", r.getName(), "priority", r.getPriority(), "status", r.getStatus()))
                .toList());
        out.put("issues", issues.listIssues(context, projectId).stream()
                .map(i -> result("id", i.getId(), "name", i.getName(), "priority", i.getPriority())).toList());
        out.put("decisions", decisions.listDecisions(context, projectId).stream()
                .map(d -> {
                    Map<String, Object> m = result("id", d.getId(), "name", d.getName());
                    d.getDecidedBy().ifPresent(u -> m.put("decided_by", accountName(u.value())));
                    return m;
                }).toList());
        return json(out);
    }

    private Map<String, Object> milestoneSummary(Milestone m) {
        Map<String, Object> out = result("id", m.getId(), "name", m.getName(), "status", m.getStatus());
        m.getDueDate().ifPresent(d -> out.put("due_date", d.toString()));
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
        private final List<Map<String, Object>> phaseItems;
        private final List<Map<String, Object>> milestoneItems;
        private final List<Map<String, Object>> listItems;
        private final List<Map<String, Object>> taskItems;
        private final Map<UUID, String> people = new HashMap<>();
        private final Set<String> existingPhases = new HashSet<>();

        Plan(RequestContext context, Map<String, Object> input) {
            this.context = context;
            this.input = input;
            this.existingProjectId = optionalUuid(input, "project_id");
            this.existing = existingProjectId.map(id -> projects.getProject(context, id));
            this.memberItems = limited(objects(input, "members"), MAX_PLAN_ITEMS, "members");
            this.phaseItems = limited(objects(input, "phases"), MAX_PLAN_ITEMS, "phases");
            this.milestoneItems = limited(objects(input, "milestones"), MAX_PLAN_ITEMS, "milestones");
            this.listItems = limited(objects(input, "task_lists"), MAX_PLAN_ITEMS, "task lists");
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
            if (existing.isPresent() && memberItems.isEmpty() && phaseItems.isEmpty() && milestoneItems.isEmpty()
                    && listItems.isEmpty() && taskItems.isEmpty()) {
                throw new ValidationException("Nothing to add was given");
            }
            optionalDate(input, "start_date");
            optionalDate(input, "target_end_date");
            optional(input, "priority").ifPresent(AssistantTools::priority);

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

            existingProjectId.ifPresent(id -> phases.listPhases(context, id)
                    .forEach(p -> existingPhases.add(key(p.getName()))));
            Set<String> phaseNames = new HashSet<>(existingPhases);
            for (Map<String, Object> p : phaseItems) {
                phaseNames.add(key(required(p, "name")));
                optionalDate(p, "start_date");
                optionalDate(p, "end_date");
            }
            for (Map<String, Object> m : milestoneItems) {
                required(m, "name");
                optionalDate(m, "due_date");
                optional(m, "phase").filter(ph -> !phaseNames.contains(key(ph))).ifPresent(ph -> {
                    throw new ValidationException("Milestone \"" + m.get("name") + "\" names a phase that does not exist: " + ph);
                });
            }
            for (Map<String, Object> l : listItems) {
                required(l, "name");
                optional(l, "phase").filter(ph -> !phaseNames.contains(key(ph))).ifPresent(ph -> {
                    throw new ValidationException("Task list \"" + l.get("name") + "\" names a phase that does not exist: " + ph);
                });
            }

            Set<String> taskNames = new HashSet<>();
            for (Map<String, Object> t : taskItems) {
                if (!taskNames.add(key(required(t, "name")))) {
                    throw new ValidationException("Two tasks are called \"" + t.get("name") + "\"; give them different names");
                }
            }
            for (Map<String, Object> t : taskItems) {
                String name = required(t, "name");
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
                optional(t, "recurrence").map(ProjectSetupTools::recurrence)
                        .filter(r -> r != TaskRecurrence.NONE && optional(t, "due_date").isEmpty())
                        .ifPresent(r -> {
                            throw new ValidationException("Task \"" + name + "\" repeats, so it needs a due date");
                        });
                optionalDate(t, "start_date");
                optionalDate(t, "due_date");
                optionalInt(t, "estimate_minutes");
                limited(strings(t, "subtasks"), MAX_PER_TASK, "subtasks on \"" + name + "\"");
                limited(strings(t, "checklist"), MAX_PER_TASK, "checklist items on \"" + name + "\"");
                for (String dep : strings(t, "depends_on")) {
                    if (!taskNames.contains(key(dep))) {
                        throw new ValidationException("Task \"" + name + "\" depends on \"" + dep
                                + "\", which is not a task in this plan");
                    }
                    if (key(dep).equals(key(name))) {
                        throw new ValidationException("Task \"" + name + "\" cannot depend on itself");
                    }
                }
            }
        }

        String describe() {
            List<String> lines = new ArrayList<>();
            if (existing.isPresent()) {
                lines.add("Add to project \"" + existing.get().getName() + "\":");
            } else {
                StringBuilder s = new StringBuilder("Create project \"" + required(input, "name") + "\"");
                optional(input, "priority").ifPresent(p -> s.append(", ").append(word(priority(p).name())).append(" priority"));
                optionalDate(input, "start_date").ifPresent(d -> s.append(", starting ").append(d));
                optionalDate(input, "target_end_date").ifPresent(d -> s.append(", finishing by ").append(d));
                lines.add(s + ":");
            }
            if (!memberItems.isEmpty()) {
                lines.add("• Team: " + String.join(", ", memberItems.stream()
                        .map(m -> people.get(uuid(m, "user_id")) + " (" + word(required(m, "role")) + ")").toList()));
            }
            if (!phaseItems.isEmpty()) {
                lines.add("• Phases: " + String.join(", ", phaseItems.stream().map(p -> {
                    String dates = optionalDate(p, "start_date").map(d -> d + " to ").orElse("")
                            + optionalDate(p, "end_date").map(LocalDate::toString).orElse("");
                    return required(p, "name") + (dates.isEmpty() ? "" : " (" + dates + ")");
                }).toList()));
            }
            if (!milestoneItems.isEmpty()) {
                lines.add("• Milestones: " + String.join(", ", milestoneItems.stream()
                        .map(m -> required(m, "name") + optionalDate(m, "due_date").map(d -> " (due " + d + ")").orElse(""))
                        .toList()));
            }
            if (!listItems.isEmpty()) {
                lines.add("• Task lists: " + String.join(", ", listItems.stream().map(l -> required(l, "name")).toList()));
            }
            if (!taskItems.isEmpty()) {
                lines.add("• " + taskItems.size() + (taskItems.size() == 1 ? " task:" : " tasks:"));
                for (Map<String, Object> t : taskItems) {
                    List<String> bits = new ArrayList<>();
                    optionalUuid(t, "assignee_id").ifPresent(a -> bits.add(people.get(a)));
                    optional(t, "priority").ifPresent(p -> bits.add(word(priority(p).name())));
                    optionalDate(t, "start_date").ifPresent(d -> bits.add("from " + d));
                    optionalDate(t, "due_date").ifPresent(d -> bits.add("due " + d));
                    optional(t, "recurrence").map(ProjectSetupTools::recurrence).filter(r -> r != TaskRecurrence.NONE)
                            .ifPresent(r -> bits.add("repeats " + word(r.name())));
                    int subs = strings(t, "subtasks").size();
                    int items = strings(t, "checklist").size();
                    if (subs > 0) {
                        bits.add(subs + (subs == 1 ? " subtask" : " subtasks"));
                    }
                    if (items > 0) {
                        bits.add(items + (items == 1 ? " checklist item" : " checklist items"));
                    }
                    List<String> deps = strings(t, "depends_on");
                    if (!deps.isEmpty()) {
                        bits.add("after " + String.join(", ", deps));
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
                        optional(input, "description").orElse(null),
                        optional(input, "priority").map(AssistantTools::priority).orElse(ProjectPriority.MEDIUM),
                        optionalDate(input, "start_date").orElse(null), optionalDate(input, "target_end_date").orElse(null)));
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

            Map<String, UUID> phaseIds = new HashMap<>();
            if (!existingPhases.isEmpty()) {
                phases.listPhases(context, projectId).forEach(p -> phaseIds.put(key(p.getName()), p.getId()));
            }
            for (Map<String, Object> p : phaseItems) {
                Phase phase = attempt(failures, "phase \"" + p.get("name") + "\"", () -> phases.createPhase(context, projectId,
                        new CreatePhaseRequest(required(p, "name"), optional(p, "description").orElse(null),
                                optionalDate(p, "start_date").orElse(null), optionalDate(p, "end_date").orElse(null))));
                if (phase != null) {
                    phaseIds.put(key(phase.getName()), phase.getId());
                }
            }

            int milestoneCount = 0;
            for (Map<String, Object> m : milestoneItems) {
                UUID phaseId = optional(m, "phase").map(ph -> phaseIds.get(key(ph))).orElse(null);
                if (attempt(failures, "milestone \"" + m.get("name") + "\"", () -> milestones.createMilestone(context, projectId,
                        new CreateMilestoneRequest(required(m, "name"), optional(m, "description").orElse(null),
                                optionalDate(m, "due_date").orElse(null), phaseId))) != null) {
                    milestoneCount++;
                }
            }

            int listCount = 0;
            for (Map<String, Object> l : listItems) {
                UUID phaseId = optional(l, "phase").map(ph -> phaseIds.get(key(ph))).orElse(null);
                if (attempt(failures, "task list \"" + l.get("name") + "\"", () -> taskLists.createTaskList(context, projectId,
                        new CreateTaskListRequest(required(l, "name"), optional(l, "description").orElse(null), phaseId))) != null) {
                    listCount++;
                }
            }

            Map<String, UUID> taskIds = new LinkedHashMap<>();
            int subCount = 0;
            int itemCount = 0;
            for (Map<String, Object> t : taskItems) {
                String taskName = required(t, "name");
                Task task = attempt(failures, "task \"" + taskName + "\"", () -> tasks.createTask(context, projectId,
                        new CreateTaskRequest(taskName, optional(t, "description").orElse(null),
                                optionalDate(t, "due_date").orElse(null), optionalUuid(t, "assignee_id").orElse(null),
                                optionalDate(t, "start_date").orElse(null),
                                optional(t, "priority").map(AssistantTools::priority).orElse(null),
                                optionalInt(t, "estimate_minutes").orElse(null),
                                optional(t, "recurrence").map(ProjectSetupTools::recurrence).orElse(null),
                                strings(t, "tags").isEmpty() ? null : strings(t, "tags"))));
                if (task == null) {
                    continue;
                }
                taskIds.put(key(taskName), task.getId());
                for (String s : strings(t, "subtasks")) {
                    if (attempt(failures, "subtask \"" + s + "\"",
                            () -> subtasks.createSubtask(context, task.getId(), new CreateSubtaskRequest(s, null))) != null) {
                        subCount++;
                    }
                }
                for (String s : strings(t, "checklist")) {
                    if (attempt(failures, "checklist item \"" + s + "\"",
                            () -> checklists.createChecklist(context, task.getId(), new CreateChecklistRequest(s, null))) != null) {
                        itemCount++;
                    }
                }
            }

            int depCount = 0;
            for (Map<String, Object> t : taskItems) {
                UUID waiting = taskIds.get(key(required(t, "name")));
                for (String dep : strings(t, "depends_on")) {
                    UUID first = taskIds.get(key(dep));
                    if (waiting == null || first == null) {
                        failures.add("dependency \"" + t.get("name") + "\" after \"" + dep + "\": a task was not created");
                        continue;
                    }
                    if (attempt(failures, "dependency \"" + t.get("name") + "\" after \"" + dep + "\"",
                            () -> dependencies.createDependency(context, waiting, new CreateDependencyRequest(first))) != null) {
                        depCount++;
                    }
                }
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("project_id", projectId);
            out.put("project", projectName);
            out.put("project_created", existing.isEmpty());
            out.put("members_added", added);
            out.put("phases_created", phaseItems.size() - countFailures(failures, "phase "));
            out.put("milestones_created", milestoneCount);
            out.put("task_lists_created", listCount);
            out.put("tasks_created", taskIds.size());
            out.put("task_ids", taskIds.entrySet().stream()
                    .map(e -> result("name", nameOf(e.getKey()), "id", e.getValue())).toList());
            out.put("subtasks_created", subCount);
            out.put("checklist_items_created", itemCount);
            out.put("dependencies_created", depCount);
            out.put("failed", failures);
            return json(out);
        }

        private String nameOf(String key) {
            return taskItems.stream().map(t -> required(t, "name")).filter(n -> key(n).equals(key)).findFirst().orElse(key);
        }
    }

    private static int countFailures(List<String> failures, String prefix) {
        return (int) failures.stream().filter(f -> f.startsWith(prefix)).count();
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

    private Subtask findSubtask(RequestContext context, Map<String, Object> input) {
        UUID id = uuid(input, "subtask_id");
        return subtasks.listSubtasks(context, uuid(input, "task_id")).stream().filter(s -> s.getId().equals(id))
                .findFirst().orElseThrow(() -> new ValidationException("No such subtask on that task"));
    }

    private Checklist findChecklistItem(RequestContext context, Map<String, Object> input) {
        UUID id = uuid(input, "item_id");
        return checklists.listChecklists(context, uuid(input, "task_id")).stream().filter(c -> c.getId().equals(id))
                .findFirst().orElseThrow(() -> new ValidationException("No such checklist item on that task"));
    }

    private Milestone findMilestone(RequestContext context, Map<String, Object> input) {
        UUID id = uuid(input, "milestone_id");
        return milestones.listMilestones(context, uuid(input, "project_id")).stream().filter(m -> m.getId().equals(id))
                .findFirst().orElseThrow(() -> new ValidationException("No such milestone on that project"));
    }

    private Risk findRisk(RequestContext context, Map<String, Object> input) {
        UUID id = uuid(input, "risk_id");
        return risks.listRisks(context, uuid(input, "project_id")).stream().filter(r -> r.getId().equals(id))
                .findFirst().orElseThrow(() -> new ValidationException("No such risk on that project"));
    }

    private String personName(RequestContext context, UUID userId) {
        return workspace.listPeople(context).stream().filter(p -> p.getId().equals(userId)).findFirst()
                .map(UserAccount::getDisplayName)
                .orElseThrow(() -> new ValidationException("That person is not in this workspace; use list_people"));
    }

    private String accountName(UUID userId) {
        return accounts.findById(userId).map(UserAccount::getDisplayName).orElse("Someone");
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

    private static Boolean bool(Map<String, Object> input, String key) {
        return optionalBool(input, key).orElseThrow(() -> new ValidationException(key + " is required"));
    }

    static TaskRecurrence recurrence(String value) {
        try {
            return TaskRecurrence.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("recurrence must be one of " + RECURRENCES);
        }
    }

    private static MilestoneStatus milestoneStatus(String value) {
        try {
            return MilestoneStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("status must be one of " + MILESTONE_STATUSES);
        }
    }

    private static RiskStatus riskStatus(String value) {
        try {
            return RiskStatus.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("status must be one of " + RISK_STATUSES);
        }
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

    private static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static Map<String, Object> bool(String description) {
        return Map.of("type", "boolean", "description", description);
    }
}
