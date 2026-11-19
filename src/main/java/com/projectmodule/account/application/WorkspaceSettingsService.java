package com.projectmodule.account.application;

import com.projectmodule.account.domain.UserAccount;
import com.projectmodule.account.domain.WorkspaceRole;
import com.projectmodule.account.domain.WorkspaceSettings;
import com.projectmodule.account.infrastructure.UserAccountRepository;
import com.projectmodule.account.infrastructure.WorkspaceSettingsRepository;
import com.projectmodule.common.context.RequestContext;
import com.projectmodule.common.exception.AuthorizationException;
import com.projectmodule.common.exception.MissingIdentityException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the app shell shows about the workspace: its name (set by an admin), the subscription plan
 * and where to get help. The plan comes from configuration; there is no billing system here.
 */
@Service
public class WorkspaceSettingsService {

    /** Shown until an admin names the workspace. */
    static final String DEFAULT_NAME = "My Workspace";

    public record Plan(String name, String validUntil, String manageUrl) {
    }

    public record Settings(String name, boolean canEdit, Plan plan, String supportEmail) {
    }

    private final WorkspaceSettingsRepository settingsRepository;
    private final UserAccountRepository accountRepository;
    private final Plan plan;
    private final String supportEmail;

    public WorkspaceSettingsService(WorkspaceSettingsRepository settingsRepository,
                                    UserAccountRepository accountRepository,
                                    @Value("${projectmodule.plan.name:Advanced}") String planName,
                                    @Value("${projectmodule.plan.valid-until:}") String planValidUntil,
                                    @Value("${projectmodule.plan.manage-url:https://workeasy360.com}") String planManageUrl,
                                    @Value("${projectmodule.support-email:support@workeasy360.com}") String supportEmail) {
        this.settingsRepository = settingsRepository;
        this.accountRepository = accountRepository;
        this.plan = new Plan(planName, planValidUntil.isBlank() ? null : planValidUntil, planManageUrl);
        this.supportEmail = supportEmail;
    }

    /**
     * The caller's workspace. A Workeasy360 sign-in has no email account here: it sees the workspace of the
     * organization it signed in to, read only (the app shell asks on every page, so this must not refuse it).
     */
    @Transactional(readOnly = true)
    public Settings get(RequestContext context) {
        Optional<UserAccount> me = accountRepository.findById(context.requireUserId().value());
        UUID organizationId = me.map(UserAccount::getOrganizationId).orElseGet(() -> organizationOf(context));
        String name = settingsRepository.findById(organizationId).map(WorkspaceSettings::getName).orElse(DEFAULT_NAME);
        boolean canEdit = me.map(account -> account.getWorkspaceRole() == WorkspaceRole.ADMIN).orElse(false);
        return new Settings(name, canEdit, plan, supportEmail);
    }

    @Transactional
    public Settings rename(RequestContext context, String newName) {
        UserAccount me = caller(context);
        if (me.getWorkspaceRole() != WorkspaceRole.ADMIN) {
            throw new AuthorizationException("Only workspace admins can rename the workspace");
        }
        UUID organizationId = me.getOrganizationId();
        WorkspaceSettings settings = settingsRepository.findById(organizationId)
                .orElseGet(() -> WorkspaceSettings.create(organizationId, newName));
        settings.rename(newName);
        settingsRepository.save(settings);
        return get(context);
    }

    private static UUID organizationOf(RequestContext context) {
        return context.organizationId()
                .orElseThrow(() -> new MissingIdentityException("No organization was supplied with this request"))
                .value();
    }

    private UserAccount caller(RequestContext context) {
        return accountRepository.findById(context.requireUserId().value())
                .orElseThrow(() -> new MissingIdentityException("Your session is no longer valid"));
    }
}
