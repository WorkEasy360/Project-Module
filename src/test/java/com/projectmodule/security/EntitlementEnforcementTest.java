package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.security.support.FakeWorkeasy360.EntitlementReply;
import com.projectmodule.security.support.SsoFlowTestBase;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The entitlement check at the HTTP boundary: what a signed-in person sees when Workeasy360 says their
 * access has ended, is read-only, cannot be verified, or cannot be reached. (The cadence and cache rules
 * themselves are in {@code EntitlementServiceTest}.)
 */
@DisplayName("Entitlement while a session lasts")
class EntitlementEnforcementTest extends SsoFlowTestBase {

    private static final String ORG = "7d1d6a35-6f0f-4c06-bd0a-0d2f4f0d4a11";

    private MockHttpSession signedIn(String person) throws Exception {
        return sessionOf(signInFresh("organization=" + ORG, spec -> spec.subject = person));
    }

    @Test
    @DisplayName("an entitled organization works; Workeasy360 is asked once for (organization, person, write)")
    void allowed() throws Exception {
        String person = UUID.randomUUID().toString();
        MockHttpSession session = signedIn(person);

        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());

        assertThat(FAKE.entitlementRequests).hasSize(1);
        assertThat(FAKE.entitlementRequests.get(0).organizationId()).isEqualTo(ORG);
        assertThat(FAKE.entitlementRequests.get(0).subject()).isEqualTo(person);
        assertThat(FAKE.entitlementRequests.get(0).write())
                .as("the read question is asked: no write parameter").isNull();
        assertThat(FAKE.assertionProblems).as("the entitlement call is client-authenticated").isEmpty();
    }

    @Test
    @DisplayName("each person is asked about separately, even in the same organization")
    void perPerson() throws Exception {
        MockHttpSession one = signedIn(UUID.randomUUID().toString());
        mvc.perform(get("/api/v1/test/whoami").session(one)).andExpect(status().isOk());
        MockHttpSession two = signedIn(UUID.randomUUID().toString());
        mvc.perform(get("/api/v1/test/whoami").session(two)).andExpect(status().isOk());

        assertThat(FAKE.entitlementRequests).hasSize(2);
    }

    @Test
    @DisplayName("ended access (expired trial / subscription) -> 403 workeasy360_access_ended with the reason")
    void accessEnded() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString());
        FAKE.entitlementHandler = request -> EntitlementReply.deny("expired");

        mvc.perform(get("/api/v1/test/whoami").session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/workeasy360-access-ended"))
                .andExpect(jsonPath("$.code").value("workeasy360_access_ended"))
                .andExpect(jsonPath("$.reason").value("expired"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("every reason the provider gives for ending access is relayed (suspended org, removed user, no seat...)")
    void reasonsAreRelayed() throws Exception {
        for (String reason : new String[] {"suspended", "not_entitled", "membership_inactive", "no_seat"}) {
            entitlements.clear();
            MockHttpSession session = signedIn(UUID.randomUUID().toString());
            FAKE.entitlementHandler = request -> EntitlementReply.deny(reason);

            mvc.perform(get("/api/v1/test/whoami").session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("workeasy360_access_ended"))
                    .andExpect(jsonPath("$.reason").value(reason));
        }
    }

    @Test
    @DisplayName("writes are refused too, and the session itself stays valid so the person can see why and sign out")
    void sessionSurvivesEndedAccess() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString());
        Cookie csrf = csrfCookie(session);
        FAKE.entitlementHandler = request -> EntitlementReply.deny("expired");

        mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("workeasy360_access_ended"));

        // Exempt: the session read and sign-out.
        mvc.perform(get("/api/auth/session").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("read-only: reads work, writes are 403 workeasy360_read_only")
    void readOnly() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString());
        Cookie csrf = csrfCookie(session);
        FAKE.entitlementHandler = request -> EntitlementReply.allow(900, false);

        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("workeasy360_read_only"))
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/workeasy360-read-only"));
    }

    @Test
    @DisplayName("a suspended / read-only organization can still read: the read question is answered allow, not deny")
    void suspendedOrganizationCanStillRead() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString());
        Cookie csrf = csrfCookie(session);
        // Workeasy360 answers the READ question for a suspended organization with allow + write_allowed:false,
        // and would answer the WRITE question with deny (the fake models both).
        FAKE.entitlementHandler = request -> EntitlementReply.allow(900, false);

        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("workeasy360_read_only"));

        assertThat(FAKE.entitlementRequests).hasSize(1);
        assertThat(FAKE.entitlementRequests).allSatisfy(request -> assertThat(request.write()).isNull());
    }

    @Test
    @DisplayName("writes work when the answer says write_allowed")
    void writeAllowed() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString());
        Cookie csrf = csrfCookie(session);

        mvc.perform(post("/api/v1/test/write").session(session).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Workeasy360 refusing this product's own client (400/401/403) fails closed: workeasy360_access_unverified")
    void refusedClientFailsClosed() throws Exception {
        for (int refusal : new int[] {400, 401, 403}) {
            entitlements.clear();
            MockHttpSession session = signedIn(UUID.randomUUID().toString());
            FAKE.entitlementHandler = request -> EntitlementReply.status(refusal);

            mvc.perform(get("/api/v1/test/whoami").session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("workeasy360_access_unverified"))
                    .andExpect(jsonPath("$.type").value("https://errors.projectmodule/workeasy360-access-unverified"));
        }
    }

    @Test
    @DisplayName("a refusal of the client is client-wide: other people and other organizations fail closed without a question")
    void refusalIsClientWide() throws Exception {
        MockHttpSession first = signedIn(UUID.randomUUID().toString());
        MockHttpSession second = sessionOf(signInFresh("organization=0e5b1f3a-9a4e-4d2c-8c11-5b7a1c2d3e4f",
                spec -> spec.subject = UUID.randomUUID().toString()));
        FAKE.entitlementHandler = request -> EntitlementReply.status(401);
        mvc.perform(get("/api/v1/test/whoami").session(first))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("workeasy360_access_unverified"));
        FAKE.entitlementHandler = request -> EntitlementReply.allow(); // even a healthy provider changes nothing for 60 s

        mvc.perform(get("/api/v1/test/whoami").session(second))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("workeasy360_access_unverified"));

        assertThat(FAKE.entitlementRequests).as("only the first question was asked").hasSize(1);
    }

    @Test
    @DisplayName("an outage (5xx, 429) is never a denial: with no answer yet, access continues")
    void outageWithNoAnswerContinues() throws Exception {
        for (int outage : new int[] {500, 502, 503, 429}) {
            entitlements.clear();
            MockHttpSession session = signedIn(UUID.randomUUID().toString());
            FAKE.entitlementHandler = request -> EntitlementReply.status(outage);

            mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("after a failed attempt the provider is left alone for a while")
    void backsOffAfterAFailure() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString());
        FAKE.entitlementHandler = request -> EntitlementReply.status(503);

        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isOk());

        assertThat(FAKE.entitlementRequests).hasSize(1);
    }

    @Test
    @DisplayName("a denial is re-asked, not kept for the allowance window: it is checked again within a minute")
    void denialIsKeptUntilReplaced() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString());
        FAKE.entitlementHandler = request -> EntitlementReply.deny("expired");
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/test/whoami").session(session)).andExpect(status().isForbidden());

        // Only one question within the 60 s window.
        assertThat(FAKE.entitlementRequests).hasSize(1);
    }

    @Test
    @DisplayName("the entitlement check does not apply to sign-in, the session read, health or static files")
    void exemptPaths() throws Exception {
        MockHttpSession session = signedIn(UUID.randomUUID().toString());
        FAKE.entitlementHandler = request -> EntitlementReply.deny("expired");
        FAKE.entitlementRequests.clear();

        mvc.perform(get("/api/auth/session").session(session)).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/workeasy360/logout").session(session)).andExpect(status().isFound());
        MvcResult root = mvc.perform(get("/").session(session)).andReturn();
        assertThat(root.getResponse().getStatus()).isNotEqualTo(403);

        assertThat(FAKE.entitlementRequests).isEmpty();
    }
}
