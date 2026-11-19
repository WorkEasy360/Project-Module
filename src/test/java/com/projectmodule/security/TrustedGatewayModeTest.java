package com.projectmodule.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.security.support.FakeWorkeasy360;
import com.projectmodule.security.support.SsoFlowTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

/**
 * Main's opt-in trusted-gateway mode ({@code PROJECTMODULE_AUTH_TRUST_IDENTITY_HEADERS=true}, off by default),
 * through the real security chain: the gateway's identity headers are the caller, the request is not identified
 * by a cookie (so CSRF does not apply), and Workeasy360's entitlement is not asked about it.
 */
@TestPropertySource(properties = "projectmodule.auth.trust-identity-headers=true")
@DisplayName("Trusted-gateway mode (opt-in)")
class TrustedGatewayModeTest extends SsoFlowTestBase {

    private static final String USER = "3c0ffee0-1111-4222-8333-444455556666";
    private static final String ORG = "3c0ffee0-aaaa-4bbb-8ccc-ddddeeeeffff";

    @Test
    @DisplayName("the headers identify the caller, for reads and CSRF-free writes; Workeasy360 is not asked")
    void headersIdentifyTheCaller() throws Exception {
        FAKE.entitlementHandler = request -> FakeWorkeasy360.EntitlementReply.deny("expired");

        mvc.perform(get("/api/v1/test/whoami").header("X-User-Id", USER).header("X-Org-Id", ORG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value(USER))
                .andExpect(jsonPath("$.organization").value(ORG));
        mvc.perform(post("/api/v1/test/write").header("X-User-Id", USER).header("X-Org-Id", ORG))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(FAKE.entitlementRequests).isEmpty();
    }

    @Test
    @DisplayName("a bearer token still takes precedence: a dead one is anonymous whatever the headers say")
    void bearerTokenTakesPrecedence() throws Exception {
        mvc.perform(get("/api/v1/test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer dead")
                        .header("X-User-Id", USER).header("X-Org-Id", ORG))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a malformed id is a 400 problem document, and the preflight now advertises the headers")
    void malformedAndPreflight() throws Exception {
        mvc.perform(get("/api/v1/test/whoami").header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://errors.projectmodule/validation-failed"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/dashboard")
                        .header(HttpHeaders.ORIGIN, "https://ops.workeasy360.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-user-id,x-org-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("x-user-id")));
    }
}
