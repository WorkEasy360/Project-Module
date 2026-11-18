package com.projectmodule.security.web;

import com.projectmodule.security.SignInErrors;
import com.projectmodule.security.Workeasy360Authentication;
import com.projectmodule.security.Workeasy360Principal;
import com.projectmodule.security.backchannel.SignInRecords;
import com.projectmodule.security.backchannel.SignInSessions;
import com.projectmodule.security.oidc.NextPath;
import com.projectmodule.security.oidc.Workeasy360OidcUserService.SignedInUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * What happens when the callback finishes.
 *
 * <p>Logs carry codes and ids, never an authorization code, token, state or nonce.
 */
public final class SignInHandlers {

    private static final Logger log = LoggerFactory.getLogger(SignInHandlers.class);

    private SignInHandlers() {
    }

    /**
     * By now Spring has already replaced the session (session fixation: {@code newSession()}, nothing
     * carried over, which also ends any previous person's session in this browser). This swaps the
     * authentication Spring built, which still holds the verified ID token, for the slim principal the
     * rest of the application uses, saves it into the fresh session and lands the browser on the app. A
     * successful sign-in also clears the "signed out of Easy Project" marker, so the next visit without a
     * session is again allowed to start a sign-in by itself.
     *
     * <p>The session is recorded against the Workeasy360 sign-in that opened it (the ID token's {@code sid},
     * when there is one) and the person, so that a back-channel logout can end it: in this instance's index of
     * its own sessions ({@link SignInSessions}), and in the record every instance shares ({@link SignInRecords}),
     * whose id the session keeps. If Workeasy360 has already ended that sign-in (its logout token overtook the
     * browser on the way back here, whichever instance it reached), the session is ended at once and the sign-in
     * fails as {@code expired}. If the shared record cannot be written, the session is ended and the sign-in
     * fails as {@code unavailable}: a session that a logout token could not reach is never handed out.
     */
    public static AuthenticationSuccessHandler success(SecurityContextRepository repository, SignedOutMarker marker,
                                                       SignInSessions signIns, SignInRecords records) {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            if (!(authentication.getPrincipal() instanceof SignedInUser user)) {
                log.error("workeasy360.sign_in_failed code={}", SignInErrors.REFUSED);
                // Spring has already saved its authentication into a new session: end that session.
                SecurityContextHolder.clearContext();
                jakarta.servlet.http.HttpSession created = request.getSession(false);
                if (created != null) {
                    created.invalidate();
                }
                fail(response, SignInErrors.REFUSED);
                return;
            }
            Workeasy360Principal principal = user.principal();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new Workeasy360Authentication(principal));
            SecurityContextHolder.setContext(context);
            repository.saveContext(context, request, response);
            HttpSession session = request.getSession(false);
            String record = null;
            String refusal = null;
            if (session == null || !signIns.opened(session, principal.issuer(), principal.subject(), user.sid())) {
                refusal = SignInErrors.EXPIRED;
            } else {
                try {
                    record = records.open(principal.issuer(), principal.subject(), user.sid());
                    if (record == null) {
                        refusal = SignInErrors.EXPIRED;
                    }
                } catch (RuntimeException failure) {
                    log.error("workeasy360.sign_in_record_failed type={}", failure.getClass().getSimpleName());
                    refusal = SignInErrors.UNAVAILABLE;
                }
            }
            if (refusal != null) {
                log.warn("workeasy360.sign_in_failed code={} reason={}", refusal,
                        SignInErrors.EXPIRED.equals(refusal) ? "sign_in_already_ended" : "sign_in_not_recorded");
                SecurityContextHolder.clearContext();
                if (session != null) {
                    session.invalidate();
                }
                fail(response, refusal);
                return;
            }
            session.setAttribute(SignInRecords.SESSION_ATTRIBUTE, record);
            marker.clear(response);
            log.info("workeasy360.sign_in user={} organization={}",
                    user.principal().userId(), user.principal().organizationId());
            SignInEntryFilter.redirect(response, NextPath.landingUrl(user.next()));
        };
    }

    /** Every failure lands on the sign-in page with a code from the fixed vocabulary; no session is created. */
    public static AuthenticationFailureHandler failure() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) -> {
            String code = SignInErrors.forFailure(request, exception);
            log.warn("workeasy360.sign_in_failed code={} cause={}", code, exception.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
            fail(response, code);
        };
    }

    private static void fail(HttpServletResponse response, String code) {
        SignInEntryFilter.redirect(response, "/#/sign-in?sso_error=" + code);
    }
}
