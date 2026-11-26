package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.agents.a2a.signature.AgentCardSignatureVerifier.Failure;
import io.apicurio.registry.agents.a2a.signature.AgentCardSignatureVerifier.Result;
import io.apicurio.registry.agents.a2a.signature.AgentCardSignatureVerifier.SignatureOutcome;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.PublicJsonWebKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCardSignatureVerifierTest {

    static final String CARD = "{\"name\":\"Weather Agent\",\"description\":\"Forecasts\","
            + "\"supportedInterfaces\":[{\"url\":\"https://weather.example.com/a2a\",\"protocolBinding\":\"JSONRPC\","
            + "\"protocolVersion\":\"1.0\"}],\"version\":\"1.0.0\",\"capabilities\":{\"streaming\":true},"
            + "\"defaultInputModes\":[\"text/plain\"],\"defaultOutputModes\":[\"text/plain\"],"
            + "\"skills\":[{\"id\":\"forecast\",\"name\":\"Forecast\",\"description\":\"Gives forecasts\",\"tags\":[]}]}";

    private static final Set<String> DEFAULT_ALGS = Set.of("ES256", "EdDSA", "RS256", "PS256");

    private static AgentCardSignatureVerifier.KeyLookup lookup(JsonWebKey... keys) {
        return (kid, jku) -> {
            List<JsonWebKey> found = new ArrayList<>();
            for (JsonWebKey key : keys) {
                if (kid.equals(key.getKeyId())) {
                    found.add(key);
                }
            }
            return found;
        };
    }

    /** The key as a verifier sees it: only the public part, parsed from a JWKS. */
    private static JsonWebKey publicOnly(PublicJsonWebKey key) {
        try {
            return new JsonWebKeySet(TestCardSigner.jwks(key)).getJsonWebKeys().get(0);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> header(Object... keyValues) {
        Map<String, Object> header = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            header.put((String) keyValues[i], keyValues[i + 1]);
        }
        return header;
    }

    @ParameterizedTest
    @ValueSource(strings = { "ES256", "EdDSA", "RS256", "PS256" })
    void validSignatureVerifiesForEachAllowedAlgorithm(String alg) {
        PublicJsonWebKey key = TestCardSigner.generateKey(alg, "key-" + alg);
        String signed = TestCardSigner.sign(CARD, key, alg);

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertTrue(result.signed());
        assertEquals(List.of(new SignatureOutcome(0, "key-" + alg, null)), result.outcomes());
        assertEquals(List.of("key-" + alg), result.verifiedKids());
    }

    @Test
    void unsignedCardIsNotSigned() {
        Result result = AgentCardSignatureVerifier.verify(CARD, lookup(), DEFAULT_ALGS);

        assertFalse(result.signed());
        assertEquals(List.of(), result.outcomes());
    }

    @Test
    void emptySignaturesArrayIsNotSigned() {
        Result result = AgentCardSignatureVerifier.verify(
                CARD.replace("{\"name\"", "{\"signatures\":[],\"name\""), lookup(), DEFAULT_ALGS);

        assertFalse(result.signed());
    }

    @Test
    void tamperedCardFailsAsInvalidSignature() {
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "key-1");
        String signed = TestCardSigner.sign(CARD, key, "ES256");
        String tampered = signed.replace("https://weather.example.com/a2a", "https://attacker.example.com/a2a");

        Result result = AgentCardSignatureVerifier.verify(tampered, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.INVALID_SIGNATURE)), result.outcomes());
        assertEquals(List.of(), result.verifiedKids());
    }

    @Test
    void reorderedPropertiesAndWhitespaceStillVerify() {
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "key-1");
        String signed = TestCardSigner.sign(CARD, key, "ES256");
        String reformatted = "{ \"version\" : \"1.0.0\", " + signed.substring(1).replace("\"version\":\"1.0.0\",", "");

        Result result = AgentCardSignatureVerifier.verify(reformatted, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertEquals(List.of("key-1"), result.verifiedKids());
    }

    @Test
    void unknownKidFailsAsUnknownKey() {
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "key-1");
        PublicJsonWebKey other = TestCardSigner.generateKey("ES256", "key-2");
        String signed = TestCardSigner.sign(CARD, key, "ES256");

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(other)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.UNKNOWN_KEY)), result.outcomes());
    }

    @Test
    void wrongKeyWithTheSameKidFailsAsInvalidSignature() {
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "key-1");
        PublicJsonWebKey impostor = TestCardSigner.generateKey("ES256", "key-1");
        String signed = TestCardSigner.sign(CARD, impostor, "ES256");

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.INVALID_SIGNATURE)), result.outcomes());
    }

    @Test
    void algNoneIsRefused() {
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "key-1");
        String signed = TestCardSigner.withSignature(CARD,
                TestCardSigner.encodeHeader("{\"alg\":\"none\",\"typ\":\"JOSE\",\"kid\":\"key-1\"}"), "AAAA");

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.DISALLOWED_ALG)), result.outcomes());
    }

    @Test
    void algorithmOutsideTheAllowListFailsAsDisallowed() {
        PublicJsonWebKey key = TestCardSigner.generateKey("RS256", "key-1");
        String signed = TestCardSigner.sign(CARD, key, "RS256");

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(key)), Set.of("ES256"));

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.DISALLOWED_ALG)), result.outcomes());
    }

    @Test
    void hmacWithThePublicKeyAsSecretIsRefused() {
        String signed = TestCardSigner.withSignature(CARD,
                TestCardSigner.encodeHeader("{\"alg\":\"HS256\",\"typ\":\"JOSE\",\"kid\":\"key-1\"}"), "AAAA");
        PublicJsonWebKey key = TestCardSigner.generateKey("RS256", "key-1");

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.DISALLOWED_ALG)), result.outcomes());
    }

    @Test
    void missingTypFails() {
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "key-1");
        String signed = TestCardSigner.sign(CARD, key, "ES256", header("alg", "ES256", "kid", "key-1"));

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.MISSING_TYP)), result.outcomes());
    }

    @Test
    void wrongTypFails() {
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "key-1");
        String signed = TestCardSigner.sign(CARD, key, "ES256", header("alg", "ES256", "typ", "JWT", "kid", "key-1"));

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.MISSING_TYP)), result.outcomes());
    }

    @Test
    void missingKidFails() {
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "key-1");
        String signed = TestCardSigner.sign(CARD, key, "ES256", header("alg", "ES256", "typ", "JOSE"));

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, null, Failure.MISSING_KID)), result.outcomes());
    }

    @Test
    void missingAlgFails() {
        String signed = TestCardSigner.withSignature(CARD,
                TestCardSigner.encodeHeader("{\"typ\":\"JOSE\",\"kid\":\"key-1\"}"), "AAAA");

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.MISSING_ALG)), result.outcomes());
    }

    @Test
    void critHeaderIsRefused() {
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "key-1");
        String signed = TestCardSigner.sign(CARD, key, "ES256",
                header("alg", "ES256", "typ", "JOSE", "kid", "key-1", "crit", List.of("exp"), "exp", 1));

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.UNSUPPORTED_CRIT)), result.outcomes());
    }

    @ParameterizedTest
    @ValueSource(strings = { "not base64!", "bm90IGpzb24", "WzFd", "" })
    void malformedProtectedHeaderFails(String protectedValue) {
        // "bm90IGpzb24" is "not json", "WzFd" is "[1]"
        String signed = TestCardSigner.withSignature(CARD, protectedValue, "AAAA");

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, null, Failure.MALFORMED)), result.outcomes());
    }

    @Test
    void keyRestrictedToAnotherAlgorithmIsNotUsed() {
        PublicJsonWebKey key = TestCardSigner.generateKey("PS256", "key-1");
        key.setAlgorithm("RS256");
        String signed = TestCardSigner.sign(CARD, key, "PS256");

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "key-1", Failure.UNKNOWN_KEY)), result.outcomes());
    }

    @Test
    void oneTrustedSignatureAmongUnknownOnesVerifies() {
        PublicJsonWebKey trusted = TestCardSigner.generateKey("ES256", "trusted");
        PublicJsonWebKey unknown = TestCardSigner.generateKey("EdDSA", "unknown");
        String signed = TestCardSigner.sign(TestCardSigner.sign(CARD, unknown, "EdDSA"), trusted, "ES256");

        Result result = AgentCardSignatureVerifier.verify(signed, lookup(publicOnly(trusted)), DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "unknown", Failure.UNKNOWN_KEY),
                new SignatureOutcome(1, "trusted", null)), result.outcomes());
        assertEquals(List.of("trusted"), result.verifiedKids());
    }

    @Test
    void duplicatePropertyNamesMakeTheCardUnverifiable() {
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "key-1");
        String signed = TestCardSigner.sign(CARD, key, "ES256");
        String duplicated = "{\"name\":\"Evil Agent\"," + signed.substring(1);

        Result result = AgentCardSignatureVerifier.verify(duplicated, lookup(publicOnly(key)), DEFAULT_ALGS);

        assertTrue(result.signed());
        assertFalse(result.canonicalizable());
        assertFalse(result.anyVerified());
    }

    @Test
    void cardSignedByTheA2aPythonSdkVerifies() throws Exception {
        String card = resource("python-signed-card.json");
        List<JsonWebKey> keys = new JsonWebKeySet(resource("python-jwks.json")).getJsonWebKeys();

        Result result = AgentCardSignatureVerifier.verify(card, lookup(keys.toArray(JsonWebKey[]::new)),
                DEFAULT_ALGS);

        assertEquals(List.of("py-es256", "py-eddsa"), result.verifiedKids());
    }

    @Test
    void cardSignedByTheA2aPythonSdkFailsOnceTampered() throws Exception {
        String card = resource("python-signed-card.json").replace("Echoes the input.", "Echoes the input!");
        List<JsonWebKey> keys = new JsonWebKeySet(resource("python-jwks.json")).getJsonWebKeys();

        Result result = AgentCardSignatureVerifier.verify(card, lookup(keys.toArray(JsonWebKey[]::new)),
                DEFAULT_ALGS);

        assertEquals(List.of(new SignatureOutcome(0, "py-es256", Failure.INVALID_SIGNATURE),
                new SignatureOutcome(1, "py-eddsa", Failure.INVALID_SIGNATURE)), result.outcomes());
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = AgentCardSignatureVerifierTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
