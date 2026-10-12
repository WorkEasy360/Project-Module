package com.projectmodule.security.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The sign-in test base with the product's RSA credential (RS256 client assertions). The EC variant
 * extends {@link SsoFlowCommon} directly with its own key.
 */
public abstract class SsoFlowTestBase extends SsoFlowCommon {

    @DynamicPropertySource
    static void rsaCredential(DynamicPropertyRegistry registry) {
        registry.add("workeasy360.key-id", () -> TestClientKeys.RSA_KEY_ID);
        registry.add("workeasy360.private-key-file", () -> TestClientKeys.RSA_FILE.toString());
    }
}
