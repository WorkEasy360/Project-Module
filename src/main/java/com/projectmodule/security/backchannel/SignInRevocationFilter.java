package com.projectmodule.security.backchannel;

import com.projectmodule.security.ProblemResponses;
import com.projectmodule.security.Workeasy360Authentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends, on whichever instance holds it, a signed-in session whose Workeasy360 sign-in has been ended by a logout
 * token delivered to any instance. It runs in the browser chain for every request that comes with a signed-in
 * session, before anything reads who the request is for, and asks the shared {@link SignInRecords} each time
 * (one primary-key read; nothing is cached).
 *
 * <ul>
 *   <li>Record open: the request goes on as usual.
 *   <li>Record gone, or no record id in the session at all: the session is invalidated here and the request
 *       goes on as anonymous, so the API answers 401 exactly as for an expired session, and the app starts a
 *       sign-in by itself. This also covers a session brought back by a restart (persisted sessions): the
 *       record is the authority, not the session.
 *   <li>The record cannot be read (the database): 503 {@code session_unverifiable}. The session is neither
 *       trusted nor ended; the next request asks again.
 * </ul>
 *
 * A "signed-in session" is one holding the {@link Workeasy360Authentication} that sign-in saves. Requests without
 * a session, and the short-lived session of a sign-in still in progress, are not looked up. Nor is a session
 * holding any other kind of authentication: sign-in never creates one, and the entitlement filter refuses it on
 * the API as it always has.
 */
public class SignInRevocationFilter extends OncePerRequestFilter {

    public static final String UNVERIFIABLE = "session_unverifiable";

    private static final Logger log = LoggerFactory.getLogger(SignInRevocationFilter.class);

    private final SignInRecords records;
    private final ProblemResponses problems;

    public SignInRevocationFilter(SignInRecords records, ProblemResponses problems) {
        this.records = records;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object recordId;
        try {
            if (session == null || !signedIn(session)) {
                filterChain.doFilter(request, response);
                return;
            }
            recordId = session.getAttribute(SignInRecords.SESSION_ATTRIBUTE);
        } catch (IllegalStateException invalidatedMeanwhile) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean open;
        try {
            open = recordId instanceof String id && records.isOpen(id);
        } catch (RuntimeException failure) {
            log.error("workeasy360.sign_in_unverifiable type={}", failure.getClass().getSimpleName());
            problems.write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "session-unverifiable",
                    "Session could not be verified",
                    "Easy Project cannot confirm your session at the moment. Try again in a moment.",
                    Map.of("code", UNVERIFIABLE));
            return;
        }
        if (!open) {
            log.info("workeasy360.session_ended reason={} record={}",
                    recordId == null ? "no_record" : "sign_in_ended", recordId == null ? "-" : recordId);
            try {
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                // Ended meanwhile: the same outcome.
            }
            // Whatever was loaded for this request from the session is dropped with it.
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    /** The session holds the authentication a Workeasy360 sign-in saves (a pending sign-in's session does not). */
    private static boolean signedIn(HttpSession session) {
        return session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
                instanceof SecurityContext context && context.getAuthentication() instanceof Workeasy360Authentication;
    }
}
