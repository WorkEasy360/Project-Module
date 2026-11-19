package com.projectmodule.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.account.api.dto.AccountResponse;
import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.common.context.ActorType;
import com.projectmodule.common.context.ImmutableRequestContext;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import com.projectmodule.common.identity.OrganizationId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROJECTMODULE_TEST_DB_URL", matches = ".+")
class AvatarServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    @Autowired
    private AvatarService avatars;

    @Autowired
    private AccountApplicationService accounts;

    @Autowired
    private WorkspaceApplicationService workspace;

    private static RequestContext as(UserAccount a) {
        return new ImmutableRequestContext(Optional.of(ExternalUserId.of(a.getId())),
                Optional.of(OrganizationId.of(a.getOrganizationId())), "corr", ActorType.HUMAN);
    }

    private UserAccount newOwner() {
        return accounts.register("o" + UUID.randomUUID() + "@example.com", "Manoj Kumar", "correct horse", true).account();
    }

    @Test
    @DisplayName("a photo is saved, seen by teammates, and its version tells clients when to fetch it")
    void uploadAndShare() {
        UserAccount owner = newOwner();
        UserAccount saved = avatars.upload(as(owner), PNG);
        assertThat(AccountResponse.from(saved).avatarVersion()).isNotNull();

        String token = workspace.invite(as(owner), "m" + UUID.randomUUID() + "@example.com").token();
        UserAccount teammate = workspace.acceptInvitation(token, "Deepti", "battery staple", false).account();
        AvatarService.Photo photo = avatars.read(as(teammate), owner.getId());
        assertThat(photo.contentType()).isEqualTo("image/png");
        assertThat(photo.content()).isEqualTo(PNG);

        UserAccount removed = avatars.remove(as(owner));
        assertThat(AccountResponse.from(removed).avatarVersion()).isNull();
        assertThatThrownBy(() -> avatars.read(as(teammate), owner.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a Workeasy360 sign-in (no email account here) sees the photos of its own organization only")
    void workeasy360SignIn() {
        UserAccount owner = newOwner();
        avatars.upload(as(owner), PNG);
        RequestContext sameOrganization = new ImmutableRequestContext(Optional.of(ExternalUserId.of(UUID.randomUUID())),
                Optional.of(OrganizationId.of(owner.getOrganizationId())), "corr", ActorType.HUMAN);
        assertThat(avatars.read(sameOrganization, owner.getId()).content()).isEqualTo(PNG);
        RequestContext otherOrganization = new ImmutableRequestContext(Optional.of(ExternalUserId.of(UUID.randomUUID())),
                Optional.of(OrganizationId.of(UUID.randomUUID())), "corr", ActorType.HUMAN);
        assertThatThrownBy(() -> avatars.read(otherOrganization, owner.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("people in another workspace cannot see a photo")
    void otherWorkspace() {
        UserAccount owner = newOwner();
        avatars.upload(as(owner), PNG);
        UserAccount stranger = newOwner();
        assertThatThrownBy(() -> avatars.read(as(stranger), owner.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("only real images are accepted, checked by content, and at most 2 MB")
    void onlyImages() {
        UserAccount owner = newOwner();
        assertThatThrownBy(() -> avatars.upload(as(owner), "<svg onload=alert(1)>".getBytes()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> avatars.upload(as(owner), new byte[AvatarService.MAX_BYTES + 1]))
                .isInstanceOf(ValidationException.class);
        assertThat(AvatarService.imageType(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).contains("image/jpeg");
        assertThat(AvatarService.imageType("GIF89a".getBytes())).contains("image/gif");
    }
}
