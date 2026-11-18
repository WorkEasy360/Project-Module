package com.projectmodule.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.account.application.AccountApplicationService;
import com.projectmodule.account.application.SignedInAccount;
import com.projectmodule.account.application.WorkspaceApplicationService;
import com.projectmodule.account.application.WorkspaceSettingsService;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.api.dto.UpdateProjectRequest;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateTaskRequest;
import com.projectmodule.work.api.dto.UpdateTaskRequest;
import com.projectmodule.work.application.TaskApplicationService;
import com.projectmodule.work.domain.Task;
import com.projectmodule.work.domain.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/** The projects list in the Operations design: customer, tags, task counts and archived projects. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class ProjectCustomerTagsAndArchiveListTest {

    @Autowired
    private ProjectApplicationService projects;

    @Autowired
    private TaskApplicationService tasks;

    @Autowired
    private AccountApplicationService accounts;

    @Autowired
    private WorkspaceApplicationService workspace;

    @Autowired
    private WorkspaceSettingsService settings;

    private final RequestContext me = new ImmutableRequestContext(Optional.of(ExternalUserId.of(UUID.randomUUID())),
            Optional.of(OrganizationId.of(UUID.randomUUID())), "corr", ActorType.HUMAN);

    @Test
    @DisplayName("a project keeps who it is for and its tags, and both can be changed or cleared")
    void customerAndTags() {
        Project p = projects.createProject(me, new CreateProjectRequest("Website Redesign", null, ProjectPriority.HIGH,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "  Rajni Machinery Store ", List.of("Website", "Marketing", "website", " ")));

        assertThat(p.getCustomerContact()).contains("Rajni Machinery Store");
        assertThat(p.getTags()).containsExactly("Website", "Marketing");

        Project updated = projects.updateProject(me, p.getId(), new UpdateProjectRequest(null, null, null, null, null, null,
                p.getVersion(), "", List.of("Ads")));
        assertThat(updated.getCustomerContact()).isEmpty();
        assertThat(updated.getTags()).containsExactly("Ads");

        assertThatThrownBy(() -> projects.createProject(me, new CreateProjectRequest("x", null, ProjectPriority.LOW, null, null,
                null, List.of("a".repeat(41))))).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("the list counts each project's live tasks and how many are completed")
    void taskCounts() {
        Project p = projects.createProject(me, new CreateProjectRequest("Launch", null, ProjectPriority.MEDIUM, null, null));
        Task done = tasks.createTask(me, p.getId(), new CreateTaskRequest("one", null, null, null));
        tasks.createTask(me, p.getId(), new CreateTaskRequest("two", null, null, null));
        tasks.updateTask(me, done.getId(), new UpdateTaskRequest(null, null, null, null, TaskStatus.COMPLETED, done.getVersion()));

        assertThat(projects.taskCounts(List.of(p.getId())).get(p.getId())).containsExactly(2, 1);
        assertThat(projects.taskCounts(List.of())).isEmpty();
    }

    @Test
    @DisplayName("archived projects leave the live list and appear in the archived one")
    void archivedList() {
        Project keep = projects.createProject(me, new CreateProjectRequest("Keep", null, ProjectPriority.MEDIUM, null, null));
        Project old = projects.createProject(me, new CreateProjectRequest("Old", null, ProjectPriority.MEDIUM, null, null));
        projects.archiveProject(me, old.getId());

        assertThat(projects.listProjects(me, PageRequest.of(0, 50)).getContent()).extracting(Project::getId)
                .contains(keep.getId()).doesNotContain(old.getId());
        assertThat(projects.listArchivedProjects(me, PageRequest.of(0, 50)).getContent()).extracting(Project::getId)
                .containsExactly(old.getId());
    }

    @Test
    @DisplayName("the workspace has a default name until its owner renames it; members cannot")
    void workspaceName() {
        SignedInAccount owner = accounts.register("o" + UUID.randomUUID() + "@example.com", "Manoj Kumar", "correct horse", true);
        RequestContext asOwner = new ImmutableRequestContext(Optional.of(ExternalUserId.of(owner.account().getId())),
                Optional.of(OrganizationId.of(owner.account().getOrganizationId())), "corr", ActorType.HUMAN);

        assertThat(settings.get(asOwner).name()).isEqualTo("My Workspace");
        assertThat(settings.get(asOwner).canEdit()).isTrue();
        assertThat(settings.rename(asOwner, " Acme Enterprises Pvt. Ltd. ").name()).isEqualTo("Acme Enterprises Pvt. Ltd.");

        String token = workspace.invite(asOwner, "m" + UUID.randomUUID() + "@example.com").token();
        SignedInAccount member = workspace.acceptInvitation(token, "Deepti", "battery staple", false);
        RequestContext asMember = new ImmutableRequestContext(Optional.of(ExternalUserId.of(member.account().getId())),
                Optional.of(OrganizationId.of(member.account().getOrganizationId())), "corr", ActorType.HUMAN);
        assertThat(settings.get(asMember).name()).isEqualTo("Acme Enterprises Pvt. Ltd.");
        assertThat(settings.get(asMember).canEdit()).isFalse();
        assertThatThrownBy(() -> settings.rename(asMember, "Hijacked")).isInstanceOf(AuthorizationException.class);
    }
}
