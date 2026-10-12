package com.projectmodule.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.projectmodule.security.support.TestClientKeys;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.ECPublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("The product's private key: RSA and EC P-256 in the PEM forms openssl writes")
class ClientKeyTest {

    @Test
    @DisplayName("RSA in PKCS#8 signs with RS256 under the configured kid")
    void rsaPkcs8() throws Exception {
        RSAKey generated = new RSAKeyGenerator(2048).keyID("x").generate();
        JWK jwk = ClientKey.parse(TestClientKeys.pem("PRIVATE KEY", generated.toRSAPrivateKey().getEncoded()), "kid-1");

        assertThat(jwk).isInstanceOf(RSAKey.class);
        assertThat(jwk.getKeyID()).isEqualTo("kid-1");
        assertThat(jwk.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwk.isPrivate()).isTrue();
        assertThat(((RSAKey) jwk).toRSAPublicKey().getModulus()).isEqualTo(generated.toRSAPublicKey().getModulus());
    }

    @Test
    @DisplayName("RSA in PKCS#1 (BEGIN RSA PRIVATE KEY) is accepted")
    void rsaPkcs1() throws Exception {
        RSAKey generated = new RSAKeyGenerator(2048).keyID("x").generate();
        byte[] pkcs1 = innerOctetString(generated.toRSAPrivateKey().getEncoded());

        JWK jwk = ClientKey.parse(TestClientKeys.pem("RSA PRIVATE KEY", pkcs1), "k");

        assertThat(((RSAKey) jwk).toRSAPublicKey().getModulus()).isEqualTo(generated.toRSAPublicKey().getModulus());
    }

    @Test
    @DisplayName("EC P-256 in PKCS#8 signs with ES256 and its public point is derived correctly")
    void ecPkcs8() throws Exception {
        ECKey generated = new ECKeyGenerator(Curve.P_256).keyID("x").generate();
        JWK jwk = ClientKey.parse(TestClientKeys.pem("PRIVATE KEY", generated.toECPrivateKey().getEncoded()), "kid-2");

        assertThat(jwk).isInstanceOf(ECKey.class);
        assertThat(jwk.getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        ECPublicKey derived = ((ECKey) jwk).toECPublicKey();
        ECPublicKey expected = generated.toECPublicKey();
        assertThat(derived.getW()).isEqualTo(expected.getW());
    }

    @Test
    @DisplayName("EC P-256 in SEC1 (BEGIN EC PRIVATE KEY) is accepted")
    void ecSec1() throws Exception {
        ECKey generated = new ECKeyGenerator(Curve.P_256).keyID("x").generate();
        byte[] sec1 = innerOctetString(generated.toECPrivateKey().getEncoded());

        JWK jwk = ClientKey.parse(TestClientKeys.pem("EC PRIVATE KEY", sec1), "k");

        assertThat(((ECKey) jwk).toECPublicKey().getW()).isEqualTo(generated.toECPublicKey().getW());
    }

    @Test
    @DisplayName("another curve, a small RSA key, an encrypted key and garbage are refused without echoing the key")
    void refusesUnusableKeys() throws Exception {
        ECKey p384 = new ECKeyGenerator(Curve.P_384).keyID("x").generate();
        RSAKey small = new RSAKeyGenerator(1024, true).keyID("x").generate();

        assertThatThrownBy(() -> ClientKey.parse(TestClientKeys.pem("PRIVATE KEY", p384.toECPrivateKey().getEncoded()), "k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientKey.parse(TestClientKeys.pem("PRIVATE KEY", small.toRSAPrivateKey().getEncoded()), "k"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2048");
        assertThatThrownBy(() -> ClientKey.parse(TestClientKeys.pem("ENCRYPTED PRIVATE KEY", new byte[] {1, 2, 3}), "k"))
                .isInstanceOf(IllegalArgumentException.class);
        String garbage = "-----BEGIN PRIVATE KEY-----\nc2VjcmV0LW1hdGVyaWFs\n-----END PRIVATE KEY-----\n";
        assertThatThrownBy(() -> ClientKey.parse(garbage, "k"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("c2VjcmV0")
                .hasMessageNotContaining("secret-material");
        assertThatThrownBy(() -> ClientKey.parse("not a pem", "k")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClientKey.parse(TestClientKeys.pem("PRIVATE KEY", new byte[] {1}), "")).isInstanceOf(
                IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a one-line variable with literal backslash-n sequences is restored; a file wins over it")
    void readsFromFileOrVariable(@TempDir Path directory) throws Exception {
        String pem = TestClientKeys.pem("PRIVATE KEY", TestClientKeys.RSA.toRSAPrivateKey().getEncoded());
        String oneLine = pem.replace("\n", "\\" + "n");
        Path file = directory.resolve("key.pem");
        Files.writeString(file, pem, StandardCharsets.UTF_8);

        assertThat(ClientKey.readPem(null, oneLine)).isEqualTo(pem);
        assertThat(ClientKey.readPem(file.toString(), "ignored")).isEqualTo(pem);
        assertThat(ClientKey.parse(ClientKey.readPem(null, oneLine), "k")).isNotNull();
        assertThatThrownBy(() -> ClientKey.readPem(directory.resolve("missing.pem").toString(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(directory.toString());
        assertThatThrownBy(() -> ClientKey.readPem("", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("scalar multiplication matches the JDK's own key pairs")
    void scalarMultiplication() throws Exception {
        ECKey generated = new ECKeyGenerator(Curve.P_256).generate();
        java.security.spec.ECParameterSpec spec = generated.toECPublicKey().getParams();
        BigInteger s = generated.toECPrivateKey().getS();

        assertThat(ClientKey.multiply(spec, s)).isEqualTo(generated.toECPublicKey().getW());
    }

    /** The content of the OCTET STRING of a PKCS#8 structure: the PKCS#1 / SEC1 key inside. */
    private static byte[] innerOctetString(byte[] pkcs8) {
        int[] position = {0};
        position[0]++; // SEQUENCE tag
        readLength(pkcs8, position);
        position[0]++; // version INTEGER tag
        int versionLength = readLength(pkcs8, position);
        position[0] += versionLength;
        position[0]++; // AlgorithmIdentifier SEQUENCE tag
        int algorithmLength = readLength(pkcs8, position);
        position[0] += algorithmLength;
        position[0]++; // OCTET STRING tag
        int length = readLength(pkcs8, position);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(pkcs8, position[0], length);
        return out.toByteArray();
    }

    private static int readLength(byte[] bytes, int[] position) {
        int first = bytes[position[0]++] & 0xff;
        if (first < 0x80) {
            return first;
        }
        int count = first & 0x7f;
        int length = 0;
        for (int i = 0; i < count; i++) {
            length = (length << 8) | (bytes[position[0]++] & 0xff);
        }
        return length;
    }
}
