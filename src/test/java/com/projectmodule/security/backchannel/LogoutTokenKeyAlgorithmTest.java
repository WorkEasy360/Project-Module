package com.projectmodule.security.backchannel;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The algorithm a logout token is verified with comes from the published key, never from the token. */
@DisplayName("Logout token keys: the algorithm is the key's")
class LogoutTokenKeyAlgorithmTest {

    @Test
    @DisplayName("an RSA key of 2048 bits or more verifies RS256 only, whether or not it says so")
    void rsa() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("k").generate().toPublicJWK();

        assertThat(LogoutTokenVerifier.algorithmOf(key)).isEqualTo(JWSAlgorithm.RS256);
        assertThat(LogoutTokenVerifier.algorithmOf(new RSAKey.Builder(key).algorithm(JWSAlgorithm.RS256).build()))
                .isEqualTo(JWSAlgorithm.RS256);
    }

    @Test
    @DisplayName("an RSA key published for another algorithm, or a short one, is not used")
    void otherRsa() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("k").generate().toPublicJWK();
        RSAKey weak = new RSAKeyGenerator(1024, true).keyID("k").generate().toPublicJWK();

        assertThat(LogoutTokenVerifier.algorithmOf(new RSAKey.Builder(key).algorithm(JWSAlgorithm.PS256).build()))
                .isNull();
        assertThat(LogoutTokenVerifier.algorithmOf(new RSAKey.Builder(key).algorithm(JWSAlgorithm.RS512).build()))
                .isNull();
        assertThat(LogoutTokenVerifier.algorithmOf(weak)).isNull();
    }

    @Test
    @DisplayName("a P-256 key verifies ES256 only; other curves and other algorithms are not used")
    void ec() throws Exception {
        ECKey p256 = new ECKeyGenerator(Curve.P_256).keyID("k").generate().toPublicJWK();
        ECKey p384 = new ECKeyGenerator(Curve.P_384).keyID("k").generate().toPublicJWK();

        assertThat(LogoutTokenVerifier.algorithmOf(p256)).isEqualTo(JWSAlgorithm.ES256);
        assertThat(LogoutTokenVerifier.algorithmOf(new ECKey.Builder(p256).algorithm(JWSAlgorithm.ES384).build()))
                .isNull();
        assertThat(LogoutTokenVerifier.algorithmOf(p384)).isNull();
    }

    @Test
    @DisplayName("a symmetric key is never used")
    void symmetric() throws Exception {
        assertThat(LogoutTokenVerifier.algorithmOf(new OctetSequenceKeyGenerator(256).keyID("k").generate()))
                .isNull();
    }
}
