package com.projectmodule.security.backchannel;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpSessionEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

@DisplayName("Which sessions each Workeasy360 sign-in opened")
class SignInSessionsTest {

    private static final String ISSUER = "https://accounts.workeasy360.com";
    private static final String OTHER_ISSUER = "https://app.workeasy360.com";

    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-17T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final SignInSessions sessions = new SignInSessions(clock);

    private MockHttpSession opened(String issuer, String subject, String sid) {
        MockHttpSession session = new MockHttpSession();
        assertThat(sessions.opened(session, issuer, subject, sid)).isTrue();
        return session;
    }

    @Test
    @DisplayName("a sid ends that sign-in's sessions of that person, under that issuer, and nothing else")
    void sid() {
        MockHttpSession named = opened(ISSUER, "pat", "sid-1");
        MockHttpSession sameSignInTwice = opened(ISSUER, "pat", "sid-1");
        MockHttpSession otherSignIn = opened(ISSUER, "pat", "sid-2");
        MockHttpSession otherIssuer = opened(OTHER_ISSUER, "pat", "sid-1");
        MockHttpSession otherPerson = opened(ISSUER, "sam", "sid-1");

        assertThat(sessions.end(ISSUER, "pat", "sid-1")).isEqualTo(2);

        assertThat(named.isInvalid()).isTrue();
        assertThat(sameSignInTwice.isInvalid()).isTrue();
        assertThat(otherSignIn.isInvalid()).isFalse();
        assertThat(otherIssuer.isInvalid()).isFalse();
        assertThat(otherPerson.isInvalid()).as("sub given: only that person's").isFalse();
        assertThat(sessions.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("only a sub ends every session of that person under that issuer, with or without a sid")
    void subjectOnly() {
        MockHttpSession a = opened(ISSUER, "pat", "sid-1");
        MockHttpSession b = opened(ISSUER, "pat", null);
        MockHttpSession otherIssuer = opened(OTHER_ISSUER, "pat", null);
        MockHttpSession otherPerson = opened(ISSUER, "sam", null);

        assertThat(sessions.end(ISSUER, "pat", null)).isEqualTo(2);

        assertThat(a.isInvalid()).isTrue();
        assertThat(b.isInvalid()).isTrue();
        assertThat(otherIssuer.isInvalid()).isFalse();
        assertThat(otherPerson.isInvalid()).isFalse();
    }

    @Test
    @DisplayName("a session that has already ended is skipped, not an error, and forgotten")
    void alreadyEnded() {
        MockHttpSession session = opened(ISSUER, "pat", "sid-1");
        session.invalidate();

        assertThat(sessions.end(ISSUER, "pat", null)).isZero();
        assertThat(sessions.size()).isZero();
        assertThat(sessions.end(ISSUER, "pat", "sid-1")).isZero();
    }

    @Test
    @DisplayName("the container's notifications keep the index in step: an ended session leaves, a new id is followed")
    void containerNotifications() {
        MockHttpSession ended = opened(ISSUER, "pat", "sid-1");
        sessions.sessionDestroyed(new HttpSessionEvent(ended));
        assertThat(sessions.size()).isZero();

        MockHttpSession renamed = opened(ISSUER, "pat", "sid-2");
        String oldId = renamed.getId();
        renamed.changeSessionId();
        sessions.sessionIdChanged(new HttpSessionEvent(renamed), oldId);
        sessions.sessionDestroyed(new HttpSessionEvent(new MockHttpSession(null, oldId)));

        assertThat(sessions.size()).as("still known under its new id").isEqualTo(1);
        assertThat(sessions.end(ISSUER, "pat", "sid-2")).isEqualTo(1);
        assertThat(renamed.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("a sign-in whose sid already ended cannot record a session, for ten minutes")
    void endedSignInIsRemembered() {
        assertThat(sessions.end(ISSUER, "pat", "sid-1")).isZero();

        assertThat(sessions.opened(new MockHttpSession(), ISSUER, "pat", "sid-1")).isFalse();
        assertThat(sessions.size()).isZero();
        assertThat(sessions.opened(new MockHttpSession(), OTHER_ISSUER, "pat", "sid-1"))
                .as("another issuer's sid of the same value").isTrue();
        assertThat(sessions.opened(new MockHttpSession(), ISSUER, "pat", null)).as("no sid: nothing to match").isTrue();

        clock.now = clock.now.plus(SignInSessions.ENDED_SIGN_IN_MEMORY);
        assertThat(sessions.opened(new MockHttpSession(), ISSUER, "pat", "sid-1")).isTrue();
    }

    @Test
    @DisplayName("a token naming only sub leaves no sid behind to refuse")
    void subjectOnlyRemembersNoSid() {
        sessions.end(ISSUER, "pat", null);

        assertThat(sessions.opened(new MockHttpSession(), ISSUER, "pat", "sid-1")).isTrue();
    }

    @Test
    @DisplayName("the memory of ended sign-ins is bounded")
    void endedSignInsAreBounded() {
        for (int i = 0; i <= SignInSessions.MAX_ENDED_SIGN_INS; i++) {
            sessions.end(ISSUER, "pat", "sid-" + i);
        }

        assertThat(sessions.opened(new MockHttpSession(), ISSUER, "pat", "sid-0")).as("the oldest was dropped").isTrue();
        assertThat(sessions.opened(new MockHttpSession(), ISSUER, "pat", "sid-" + SignInSessions.MAX_ENDED_SIGN_INS))
                .isFalse();
    }
}
