package com.projectmodule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.projectmodule.security.support.SsoFlowCommon;
import com.projectmodule.security.support.TestClientKeys;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** The same flow with an EC P-256 credential (ES256 client assertions) instead of RSA. */
@DisplayName("private_key_jwt with an EC P-256 key")
class SignInEcKeyTest extends SsoFlowCommon {

    @DynamicPropertySource
    static void ecCredential(DynamicPropertyRegistry registry) {
        registry.add("workeasy360.key-id", () -> TestClientKeys.EC_KEY_ID);
        registry.add("workeasy360.private-key-file", () -> TestClientKeys.EC_FILE.toString());
    }

    @Test
    @DisplayName("the token request and the entitlement call both carry a valid ES256 client assertion")
    void signsWithEs256() throws Exception {
        String person = UUID.randomUUID().toString();
        MvcResult result = signInFresh(null, spec -> spec.subject = person);
        assertThat(ssoError(result)).isNull();
        MockHttpSession session = sessionOf(result);

        mvc.perform(get("/api/v1/test/whoami").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value(person));

        assertThat(FAKE.assertionProblems).isEmpty();
        assertThat(FAKE.tokenRequests).hasSize(1);
        assertThat(FAKE.entitlementRequests).hasSize(1);
        assertThat(FAKE.tokenRequests.get(0).get("client_assertion"))
                .isNotBlank().satisfies(jwt -> assertThat(decodeHeader(jwt)).contains("\"ES256\"", "client-ec-1"));
    }

    private static String decodeHeader(String jwt) {
        return new String(java.util.Base64.getUrlDecoder().decode(jwt.substring(0, jwt.indexOf('.'))),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
