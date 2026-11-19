package com.projectmodule.account.application;

import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ResourceNotFoundException;
import com.projectmodule.common.exception.ValidationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile photos. A photo must be a real PNG, JPEG, WebP or GIF image (checked by its first bytes,
 * not by what the browser claims) of at most 2 MB, and is shown only to people in the same
 * workspace. SVG is refused, since it can carry script.
 */
@Service
public class AvatarService {

    public static final int MAX_BYTES = 2 * 1024 * 1024;

    public record Photo(String contentType, byte[] content, Instant updatedAt) {
    }

    private final UserAccountRepository accounts;
    private final JdbcTemplate jdbc;

    public AvatarService(UserAccountRepository accounts, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    @Transactional
    public UserAccount upload(RequestContext context, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException("Choose a photo to upload");
        }
        if (content.length > MAX_BYTES) {
            throw new ValidationException("The photo must be at most 2 MB");
        }
        String type = imageType(content)
                .orElseThrow(() -> new ValidationException("The photo must be a PNG, JPEG, WebP or GIF image"));
        UserAccount me = caller(context);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO user_avatars (account_id, content_type, content, updated_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (account_id) DO UPDATE SET content_type = EXCLUDED.content_type, "
                        + "content = EXCLUDED.content, updated_at = EXCLUDED.updated_at",
                me.getId(), type, content, Timestamp.from(now));
        me.markAvatarChanged(now);
        return accounts.save(me);
    }

    @Transactional
    public UserAccount remove(RequestContext context) {
        UserAccount me = caller(context);
        jdbc.update("DELETE FROM user_avatars WHERE account_id = ?", me.getId());
        me.markAvatarChanged(null);
        return accounts.save(me);
    }

    /** Someone's photo, for a person in the same workspace. */
    @Transactional(readOnly = true)
    public Photo read(RequestContext context, UUID accountId) {
        UUID organizationId = callerOrganization(context);
        UserAccount them = accounts.findById(accountId)
                .filter(a -> a.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException("Photo", accountId));
        List<Photo> found = jdbc.query("SELECT content_type, content, updated_at FROM user_avatars WHERE account_id = ?",
                (rs, n) -> new Photo(rs.getString(1), rs.getBytes(2), rs.getTimestamp(3).toInstant()), them.getId());
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("Photo", accountId);
        }
        return found.get(0);
    }

    /** The image type, from the file's first bytes. */
    static Optional<String> imageType(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return Optional.of("image/png");
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Optional.of("image/webp");
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return Optional.of("image/gif");
        }
        return Optional.empty();
    }

    /**
     * The caller's workspace: their email account's or, for a Workeasy360 sign-in (which has no email account
     * here, and so no photo of its own), the organization it signed in to.
     */
    private UUID callerOrganization(RequestContext context) {
        return accounts.findById(context.requireUserId().value())
                .map(UserAccount::getOrganizationId)
                .orElseGet(() -> context.organizationId()
                        .orElseThrow(() -> new MissingIdentityException("Your session is no longer valid"))
                        .value());
    }

    private UserAccount caller(RequestContext context) {
        return accounts.findById(context.requireUserId().value())
                .orElseThrow(() -> new MissingIdentityException("Your session is no longer valid"));
    }
}
