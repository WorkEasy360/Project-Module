package com.projectmodule.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.domain.WorkspaceRole;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.AuthenticatedIdentity;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.BusinessRuleViolationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.api.dto.CreateProjectRequest;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Someone who used the product before email sign-in moves onto an email and password and keeps
 * their work: against a real PostgreSQL, with the real wiring.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class LegacyHandoverServiceTest {

    @Autowired
    private LegacyHandoverService handover;

    @Autowired
    private AccountApplicationService accounts;

    @Autowired
    private ProjectApplicationService projects;

    @Autowired
    private UserAccountRepository accountRepository;

    private final UUID organization = UUID.randomUUID();
    private final UUID oldUser = UUID.randomUUID();

    private Project earlierWork() {
        RequestContext asBefore = new ImmutableRequestContext(Optional.of(ExternalUserId.of(oldUser)),
                Optional.of(OrganizationId.of(organization)), "corr", ActorType.HUMAN);
        return projects.createProject(asBefore,
                new CreateProjectRequest("Launch plan", null, ProjectPriority.HIGH, null, null));
    }

    private static String email() {
        return "ada" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("the new account takes the old ids, so the earlier work is still theirs, and the password signs in")
    void keepsTheirWork() {
        Project project = earlierWork();
        String email = email();

        SignedInAccount signedIn = handover.claim(oldUser, organization, email, "Ada", "correct horse", true);

        assertThat(signedIn.account().getId()).isEqualTo(oldUser);
        assertThat(signedIn.account().getOrganizationId()).isEqualTo(organization);
        assertThat(signedIn.account().getWorkspaceRole()).isEqualTo(WorkspaceRole.ADMIN);
        AuthenticatedIdentity who = accounts.authenticate(signedIn.token()).orElseThrow();
        assertThat(who.userId().value()).isEqualTo(oldUser);
        RequestContext now = new ImmutableRequestContext(Optional.of(who.userId()), Optional.of(who.organizationId()),
                "corr", ActorType.HUMAN);
        assertThat(projects.getProject(now, project.getId()).getName()).isEqualTo("Launch plan");
        assertThat(accounts.login(email, "correct horse", false).account().getId()).isEqualTo(oldUser);
    }

    @Test
    @DisplayName("it works once per id: afterwards only the password gets in")
    void onlyOnce() {
        earlierWork();
        handover.claim(oldUser, organization, email(), "Ada", "correct horse", true);

        assertThatThrownBy(() -> handover.claim(oldUser, organization, email(), "Mallory", "another pass", true))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("ids with no earlier work, or the wrong organization, cannot be claimed")
    void needsEarlierWork() {
        earlierWork();

        assertThatThrownBy(() -> handover.claim(UUID.randomUUID(), organization, email(), "X", "correct horse", true))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> handover.claim(oldUser, UUID.randomUUID(), email(), "X", "correct horse", true))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(accountRepository.existsById(oldUser)).isFalse();
    }

    @Test
    @DisplayName("the second person to move over in a workspace joins it as a member")
    void secondPersonIsMember() {
        earlierWork();
        handover.claim(oldUser, organization, email(), "Ada", "correct horse", true);
        UUID colleague = UUID.randomUUID();
        RequestContext asColleague = new ImmutableRequestContext(Optional.of(ExternalUserId.of(colleague)),
                Optional.of(OrganizationId.of(organization)), "corr", ActorType.HUMAN);
        projects.createProject(asColleague, new CreateProjectRequest("Hiring", null, ProjectPriority.LOW, null, null));

        UserAccount account = handover.claim(colleague, organization, email(), "Ben", "battery staple", false).account();

        assertThat(account.getWorkspaceRole()).isEqualTo(WorkspaceRole.MEMBER);
    }
}
