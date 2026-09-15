package com.projectmodule.common.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectmodule.common.exception.MissingIdentityException;
import com.projectmodule.common.exception.ValidationException;
import com.projectmodule.common.identity.ExternalUserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestContextFactoryTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static final String ORG = "22222222-2222-2222-2222-222222222222";

    private final RequestContextFactory factory = new RequestContextFactory();

    @Test
    @DisplayName("reads identity from the gateway headers")
    void readsIdentity() {
        RequestContext context = factory.fromHeaders(USER, ORG, "corr-1");

        assertThat(context.userId()).contains(ExternalUserId.of(UUID.fromString(USER)));
        assertThat(context.organizationId()).isPresent();
        assertThat(context.correlationId()).isEqualTo("corr-1");
    }

    @Test
    @DisplayName("accepts a request with no identity rather than rejecting it")
    void allowsAnonymous() {
        RequestContext context = factory.fromHeaders(null, null, null);

        assertThat(context.userId()).isEmpty();
        assertThat(context.organizationId()).isEmpty();
    }

    @Test
    @DisplayName("treats a blank header as absent")
    void treatsBlankAsAbsent() {
        assertThat(factory.fromHeaders("   ", "", null).userId()).isEmpty();
    }

    @Test
    @DisplayName("trims surrounding whitespace")
    void trimsWhitespace() {
        assertThat(factory.fromHeaders("  " + USER + "  ", null, null).userId())
                .contains(ExternalUserId.of(UUID.fromString(USER)));
    }

    @Test
    @DisplayName("generates a correlation id when the caller supplies none")
    void generatesCorrelationId() {
        String correlationId = factory.fromHeaders(USER, ORG, null).correlationId();

        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    @DisplayName("rejects a malformed user identifier")
    void rejectsMalformedUser() {
        assertThatThrownBy(() -> factory.fromHeaders("not-a-uuid", ORG, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects a malformed organization identifier")
    void rejectsMalformedOrganization() {
        assertThatThrownBy(() -> factory.fromHeaders(USER, "not-a-uuid", null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("never lets a caller claim to be the AI")
    void actorTypeIsAlwaysHuman() {
        assertThat(factory.fromHeaders(USER, ORG, null).actorType()).isEqualTo(ActorType.HUMAN);
    }

    @Test
    @DisplayName("requireUserId fails when the request carried no identity")
    void requireUserIdFailsWhenAbsent() {
        RequestContext context = factory.fromHeaders(null, null, null);

        assertThatThrownBy(context::requireUserId)
                .isInstanceOf(MissingIdentityException.class);
    }

    @Test
    @DisplayName("requireUserId returns the caller when identity is present")
    void requireUserIdReturnsCaller() {
        assertThat(factory.fromHeaders(USER, null, null).requireUserId())
                .isEqualTo(ExternalUserId.of(UUID.fromString(USER)));
    }
}
