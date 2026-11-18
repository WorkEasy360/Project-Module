package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.projectmodule.security.backchannel.SignInRecords;
import com.projectmodule.security.oidc.ProviderKeySet;
import com.projectmodule.security.support.FakeWorkeasy360.LogoutTokenSpec;
import com.projectmodule.security.support.SsoFlowTestBase;
import com.projectmodule.security.support.SsoTestApplication;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * The shared sign-in record, through the real security chain: a session whose sign-in a logout token ended on
 * <em>another</em> instance (only the shared record knows: this instance's own index and its sessions are left
 * untouched) is ended at its next request here; the callback consults the shared record too; and nothing is
 * trusted when the record cannot be read. The record is the in-memory stand-in for the PostgreSQL one; the
 * PostgreSQL one and two real instances are proved in {@code SignInRecordsPersistenceTest} and
 * {@code MultiInstanceBackchannelLogoutTest}.
 */
@DisplayName("Sessions ended elsewhere: the shared sign-in record is checked on every signed-in request")
class SignInRevocationTest extends SsoFlowTestBase {

    @Autowired
    private SsoTestApplication.InMemorySignInRecords records;

    @Autowired
    private SsoTestApplication.InMemoryReceivedLogoutTokens received;

    @Autowired
    private ProviderKeySet keySet;

    @BeforeEach
    void freshState() {
        records.clear();
        received.clear();
        keySet.forget();
    }

    @Test
    @DisplayName("a sid ended on another instance ends this instance's session at its next request, and only that one")
    void sidEndedElsewhere() throws Exception {
        String person = uuid();
        String sid = uuid();
        MockHttpSession laptop = signedIn(person, sid);
        MockHttpSession phone = signedIn(person, uuid());
        assertThat(status(laptop, "/api/v1/test/whoami")).isEqualTo(200);

        // What a logout token delivered to another instance does: the shared rows go, nothing here is touched.
        assertThat(records.end(FAKE.issuer(), person, sid)).isEqualTo(1);
        assertThat(laptop.isInvalid()).as("not yet: this instance has not seen a request").isFalse();

        assertThat(status(laptop, "/api/v1/test/whoami")).isEqualTo(401);
        assertThat(laptop.isInvalid()).as("ended here, at that request").isTrue();
        assertThat(status(laptop, "/api/auth/session")).isEqualTo(401);
        assertThat(status(phone, "/api/v1/test/whoami")).as("the same person's other sign-in").isEqualTo(200);
    }

    @Test
    @DisplayName("only sub, ended on another instance: every session of that person here ends; others' do not")
    void subjectEndedElsewhere() throws Exception {
        String person = uuid();
        MockHttpSession first = signedIn(person, uuid());
        MockHttpSession withoutSid = signedIn(person, null);
        MockHttpSession colleague = signedIn(uuid(), uuid());

        assertThat(records.end(FAKE.issuer(), person, null)).isEqualTo(2);

        assertThat(status(first, "/api/auth/session")).isEqualTo(401);
        assertThat(status(withoutSid, "/api/v1/test/whoami")).isEqualTo(401);
        assertThat(status(colleague, "/api/v1/test/whoami")).isEqualTo(200);
    }

    @Test
    @DisplayName("the endpoint ends the shared record too, so every other instance's copy of the sign-in ends")
    void endpointEndsTheSharedRecord() throws Exception {
        String person = uuid();
        String sid = uuid();
        MockHttpSession session = signedIn(person, sid);
        String record = (String) session.getAttribute(SignInRecords.SESSION_ATTRIBUTE);
        assertThat(records.isOpen(record)).isTrue();

        assertThat(deliver(token(spec -> {
            spec.subject = person;
            spec.sid = sid;
        })).getResponse().getStatus()).isEqualTo(200);

        assertThat(records.isOpen(record)).isFalse();
    }

    @Test
    @DisplayName("a signed-in session that carries no record is not trusted: it is ended")
    void sessionWithoutARecord() throws Exception {
        MockHttpSession session = signedIn(uuid(), uuid());
        session.removeAttribute(SignInRecords.SESSION_ATTRIBUTE);

        assertThat(status(session, "/api/auth/session")).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("the record cannot be read: 503 session_unverifiable, no-store; the session is neither used nor ended")
    void recordUnreadable() throws Exception {
        MockHttpSession session = signedIn(uuid(), uuid());
        records.failNextWith(new CannotCreateTransactionException("database unavailable"));

        MvcResult result = mvc.perform(get("/api/v1/test/whoami").session(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(result.getResponse().getContentAsString()).contains("\"session_unverifiable\"")
                .doesNotContain("user");
        assertThat(result.getResponse().getHeaders("Cache-Control")).containsExactly("no-store");
        assertThat(session.isInvalid()).isFalse();

        assertThat(status(session, "/api/v1/test/whoami")).as("the next request asks again").isEqualTo(200);
    }

    @Test
    @DisplayName("a write with an ended session is refused too (401), never reaching the controller")
    void writeWithAnEndedSession() throws Exception {
        String person = uuid();
        MockHttpSession session = signedIn(person, uuid());
        jakarta.servlet.http.Cookie csrf = csrfCookie(session);
        assertThat(mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andReturn().getResponse().getStatus()).as("before: the write goes through").isEqualTo(204);
        records.end(FAKE.issuer(), person, null);

        MvcResult result = mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("requests without a signed-in session are not looked up: anonymous ones, and a sign-in in progress")
    void onlySignedInSessionsAreLookedUp() throws Exception {
        mvc.perform(get("/api/auth/session")).andReturn();
        mvc.perform(get("/")).andReturn();
        Started started = start(null);
        mvc.perform(get("/api/auth/session").session(started.session())).andReturn();

        assertThat(records.lookups()).isZero();

        MockHttpSession signedIn = sessionOf(signIn(started, spec -> spec.subject = uuid()));
        assertThat(signedIn).isNotNull();
        assertThat(records.lookups()).as("the callback itself comes with the sign-in's own session").isZero();
        assertThat(status(signedIn, "/api/auth/session")).isEqualTo(200);
        assertThat(records.lookups()).as("one look-up per signed-in request, none cached").isEqualTo(1);
        assertThat(status(signedIn, "/api/auth/session")).isEqualTo(200);
        assertThat(records.lookups()).isEqualTo(2);
    }

    @Test
    @DisplayName("a sign-in overtaken by its logout on another instance is refused (expired) and leaves no session")
    void overtakenElsewhere() throws Exception {
        String person = uuid();
        String sid = uuid();
        Started started = start(null);
        String code = FAKE.issueCode(started.authorization(), spec -> {
            spec.subject = person;
            spec.extra.put("sid", sid);
        });
        // Only the shared record hears of it: this instance's own memory of ended sign-ins does not.
        records.end(FAKE.issuer(), person, sid);

        MvcResult callback = callback(started, code, started.authorization().state());

        assertThat(ssoError(callback)).isEqualTo("expired");
        assertThat(sessionOf(callback)).isNull();
        assertThat(records.size()).isZero();
    }

    @Test
    @DisplayName("the shared record cannot be written at sign-in: the sign-in fails as unavailable and leaves no session")
    void recordNotWritten() throws Exception {
        Started started = start(null);
        records.failNextWith(new CannotCreateTransactionException("database unavailable"));

        MvcResult callback = signIn(started, spec -> spec.subject = uuid());

        assertThat(ssoError(callback)).isEqualTo("unavailable");
        assertThat(sessionOf(callback)).isNull();
        assertThat(status(started.session(), "/api/auth/session")).isEqualTo(401);
    }

    // --- helpers -----------------------------------------------------------------------------------------

    private MockHttpSession signedIn(String subject, String sid) throws Exception {
        MvcResult result = signInFresh(null, spec -> {
            spec.subject = subject;
            if (sid != null) {
                spec.extra.put("sid", sid);
            }
        });
        assertThat(ssoError(result)).isNull();
        MockHttpSession session = sessionOf(result);
        assertThat(session.getAttribute(SignInRecords.SESSION_ATTRIBUTE)).as("the session carries its record")
                .isInstanceOf(String.class);
        return session;
    }

    private int status(MockHttpSession session, String path) throws Exception {
        return mvc.perform(get(path).session(session)).andReturn().getResponse().getStatus();
    }

    private static String token(Consumer<LogoutTokenSpec> customizer) throws Exception {
        LogoutTokenSpec spec = new LogoutTokenSpec();
        customizer.accept(spec);
        return FAKE.mintLogoutToken(spec);
    }

    private MvcResult deliver(String logoutToken) throws Exception {
        return mvc.perform(post("/api/auth/workeasy360/backchannel-logout")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("logout_token", logoutToken))
                .andReturn();
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
