package com.projectmodule.work.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.ConflictException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import com.projectmodule.project.application.ProjectApplicationService;
import com.projectmodule.project.application.authorization.ProjectAuthorizationService;
import com.projectmodule.project.application.authorization.ProjectPermission;
import com.projectmodule.project.domain.Project;
import com.projectmodule.project.domain.ProjectPriority;
import com.projectmodule.work.api.dto.CreateCommentRequest;
import com.projectmodule.work.api.dto.UpdateCommentRequest;
import com.projectmodule.work.domain.ProjectComment;
import com.projectmodule.work.infrastructure.ProjectCommentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ProjectCommentApplicationServiceTest {

    private static final OrganizationId ORG = OrganizationId.of(UUID.randomUUID());
    private static final ExternalUserId AUTHOR = ExternalUserId.of(UUID.randomUUID());
    private static final ExternalUserId OTHER_MEMBER = ExternalUserId.of(UUID.randomUUID());

    @Mock
    private ProjectCommentRepository commentRepository;

    @Mock
    private ProjectApplicationService projectApplicationService;

    @Mock
    private ProjectAuthorizationService projectAuthorizationService;

    private ProjectCommentApplicationService service;
    private RequestContext authorContext;
    private RequestContext otherMemberContext;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectCommentApplicationService(
                commentRepository, projectApplicationService, projectAuthorizationService);
        authorContext = new ImmutableRequestContext(
                Optional.of(AUTHOR), Optional.of(ORG), "corr-1", ActorType.HUMAN);
        otherMemberContext = new ImmutableRequestContext(
                Optional.of(OTHER_MEMBER), Optional.of(ORG), "corr-2", ActorType.HUMAN);
        project = Project.create(ORG, "Apollo", AUTHOR, ProjectPriority.MEDIUM);
    }

    @Nested
    class Create {

        @Test
        @DisplayName("creates a comment authored by the caller, requiring only VIEW_PROJECT")
        void createsComment() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(authorContext, project.getId()))
                    .thenReturn(project);
            when(commentRepository.save(any(ProjectComment.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateCommentRequest request = new CreateCommentRequest("Looks good to me");
            ProjectComment created = service.createComment(authorContext, project.getId(), request);

            assertThat(created.getAuthorId()).isEqualTo(AUTHOR);
            assertThat(created.getBody()).isEqualTo("Looks good to me");
            verify(projectAuthorizationService).requirePermission(
                    AUTHOR, project.getId(), ProjectPermission.VIEW_PROJECT);
        }

        @Test
        @DisplayName("denies creation without VIEW_PROJECT")
        void deniesWithoutPermission() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(authorContext, project.getId()))
                    .thenReturn(project);
            doThrow(new AuthorizationException("denied"))
                    .when(projectAuthorizationService)
                    .requirePermission(AUTHOR, project.getId(), ProjectPermission.VIEW_PROJECT);

            CreateCommentRequest request = new CreateCommentRequest("Looks good to me");

            assertThatThrownBy(() -> service.createComment(authorContext, project.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("denies creation across an organization boundary")
        void deniesCrossOrganization() {
            when(projectApplicationService.findActiveProjectInCallerOrganization(authorContext, project.getId()))
                    .thenThrow(new ResourceNotFoundException("Project", project.getId()));

            CreateCommentRequest request = new CreateCommentRequest("Looks good to me");

            assertThatThrownBy(() -> service.createComment(authorContext, project.getId(), request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(commentRepository, never()).save(any());
        }
    }

    @Nested
    class List_ {

        @Test
        @DisplayName("lists a page of comments for the project, requiring VIEW_PROJECT")
        void listsComments() {
            ProjectComment comment = ProjectComment.create(project.getId(), AUTHOR, "Looks good to me");
            Pageable pageable = PageRequest.of(0, 20);
            when(projectApplicationService.findActiveProjectInCallerOrganization(authorContext, project.getId()))
                    .thenReturn(project);
            when(commentRepository.findByProjectIdAndArchivedAtIsNull(project.getId(), pageable))
                    .thenReturn(new PageImpl<>(List.of(comment), pageable, 1));

            var page = service.listComments(authorContext, project.getId(), pageable);

            assertThat(page.getContent()).containsExactly(comment);
            verify(projectAuthorizationService).requirePermission(
                    AUTHOR, project.getId(), ProjectPermission.VIEW_PROJECT);
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("the author can edit their own comment")
        void authorCanEdit() {
            ProjectComment comment = ProjectComment.create(project.getId(), AUTHOR, "Looks good to me");
            when(commentRepository.findByIdAndArchivedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
            when(projectApplicationService.findActiveProjectInCallerOrganization(authorContext, project.getId()))
                    .thenReturn(project);
            when(commentRepository.save(any(ProjectComment.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateCommentRequest request = new UpdateCommentRequest("Actually, one concern", comment.getVersion());
            ProjectComment updated = service.updateComment(authorContext, comment.getId(), request);

            assertThat(updated.getBody()).isEqualTo("Actually, one concern");
        }

        @Test
        @DisplayName("a non-author is denied, even with EDIT_PROJECT")
        void nonAuthorCannotEdit() {
            ProjectComment comment = ProjectComment.create(project.getId(), AUTHOR, "Looks good to me");
            when(commentRepository.findByIdAndArchivedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
            when(projectApplicationService.findActiveProjectInCallerOrganization(otherMemberContext, project.getId()))
                    .thenReturn(project);

            UpdateCommentRequest request = new UpdateCommentRequest("Hijacked", comment.getVersion());

            assertThatThrownBy(() -> service.updateComment(otherMemberContext, comment.getId(), request))
                    .isInstanceOf(AuthorizationException.class);
            verify(commentRepository, never()).save(any());
            verify(projectAuthorizationService, never()).hasPermission(any(), any(), any());
        }

        @Test
        @DisplayName("rejects an update against a stale version")
        void rejectsStaleVersion() {
            ProjectComment comment = ProjectComment.create(project.getId(), AUTHOR, "Looks good to me");
            when(commentRepository.findByIdAndArchivedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
            when(projectApplicationService.findActiveProjectInCallerOrganization(authorContext, project.getId()))
                    .thenReturn(project);

            UpdateCommentRequest request = new UpdateCommentRequest("Renamed", comment.getVersion() + 1);

            assertThatThrownBy(() -> service.updateComment(authorContext, comment.getId(), request))
                    .isInstanceOf(ConflictException.class);
            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("reports not found for a missing comment")
        void reportsNotFound() {
            UUID id = UUID.randomUUID();
            when(commentRepository.findByIdAndArchivedAtIsNull(id)).thenReturn(Optional.empty());

            UpdateCommentRequest request = new UpdateCommentRequest("Text", 0L);

            assertThatThrownBy(() -> service.updateComment(authorContext, id, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        @DisplayName("the author can archive their own comment")
        void authorCanArchive() {
            ProjectComment comment = ProjectComment.create(project.getId(), AUTHOR, "Looks good to me");
            when(commentRepository.findByIdAndArchivedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
            when(projectApplicationService.findActiveProjectInCallerOrganization(authorContext, project.getId()))
                    .thenReturn(project);
            when(commentRepository.save(any(ProjectComment.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveComment(authorContext, comment.getId());

            assertThat(comment.isArchived()).isTrue();
        }

        @Test
        @DisplayName("a non-author with EDIT_PROJECT can moderate-archive the comment")
        void moderatorCanArchive() {
            ProjectComment comment = ProjectComment.create(project.getId(), AUTHOR, "Looks good to me");
            when(commentRepository.findByIdAndArchivedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
            when(projectApplicationService.findActiveProjectInCallerOrganization(otherMemberContext, project.getId()))
                    .thenReturn(project);
            when(projectAuthorizationService.hasPermission(
                    OTHER_MEMBER, project.getId(), ProjectPermission.EDIT_PROJECT)).thenReturn(true);
            when(commentRepository.save(any(ProjectComment.class))).thenAnswer(inv -> inv.getArgument(0));

            service.archiveComment(otherMemberContext, comment.getId());

            assertThat(comment.isArchived()).isTrue();
            assertThat(comment.getArchivedBy()).contains(OTHER_MEMBER);
        }

        @Test
        @DisplayName("a non-author without EDIT_PROJECT is denied")
        void nonAuthorWithoutPermissionCannotArchive() {
            ProjectComment comment = ProjectComment.create(project.getId(), AUTHOR, "Looks good to me");
            when(commentRepository.findByIdAndArchivedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
            when(projectApplicationService.findActiveProjectInCallerOrganization(otherMemberContext, project.getId()))
                    .thenReturn(project);
            when(projectAuthorizationService.hasPermission(
                    OTHER_MEMBER, project.getId(), ProjectPermission.EDIT_PROJECT)).thenReturn(false);

            assertThatThrownBy(() -> service.archiveComment(otherMemberContext, comment.getId()))
                    .isInstanceOf(AuthorizationException.class);
            verify(commentRepository, never()).save(any());
        }
    }
}
