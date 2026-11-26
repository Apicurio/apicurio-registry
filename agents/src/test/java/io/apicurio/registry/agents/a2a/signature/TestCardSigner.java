package io.apicurio.registry.agents.a2a.signature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.agents.signature.AgentCardSigningInput;
import io.apicurio.registry.agents.signature.JsonCanonicalizer;
import org.jose4j.jca.ProviderContext;
import org.jose4j.jwa.AlgorithmFactoryFactory;
import org.jose4j.jwk.EcJwkGenerator;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.OctetKeyPairJsonWebKey;
import org.jose4j.jwk.OkpJwkGenerator;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.JsonWebSignatureAlgorithm;
import org.jose4j.keys.EllipticCurves;
import org.jose4j.lang.JoseException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signs Agent Cards in tests the way A2A v1.0, section 8.4 describes: a JWS whose payload is the RFC 8785
 * canonical form of the card without {@code signatures}, appended to {@code signatures[]} as its
 * {@code protected} and {@code signature} parts.
 */
public final class TestCardSigner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestCardSigner() {
    }

    /** A public/private key pair for the algorithm, with the given {@code kid}. */
    public static PublicJsonWebKey generateKey(String alg, String kid) {
        try {
            PublicJsonWebKey key = switch (alg) {
                case "ES256" -> EcJwkGenerator.generateJwk(EllipticCurves.P256);
                case "EdDSA" -> OkpJwkGenerator.generateJwk(OctetKeyPairJsonWebKey.SUBTYPE_ED25519);
                case "RS256", "PS256" -> RsaJwkGenerator.generateJwk(2048);
                default -> throw new IllegalArgumentException(alg);
            };
            key.setKeyId(kid);
            return key;
        } catch (JoseException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The JSON Web Key Set of the keys, public parts only. */
    public static String jwks(JsonWebKey... keys) {
        return new JsonWebKeySet(Arrays.asList(keys)).toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
    }

    /** Signs the card with a standard protected header: {@code alg}, {@code typ: JOSE} and the key's kid. */
    public static String sign(String cardJson, PublicJsonWebKey key, String alg) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", alg);
        header.put("typ", "JOSE");
        header.put("kid", key.getKeyId());
        return sign(cardJson, key, alg, header);
    }

    /**
     * Signs the card with {@code alg} and the key, under exactly the given protected header (which may
     * lack or contradict members, for negative tests).
     */
    public static String sign(String cardJson, PublicJsonWebKey key, String alg, Map<String, Object> header) {
        try {
            String encodedHeader = encode(MAPPER.writeValueAsBytes(header));
            byte[] payload = AgentCardSigningInput.payloads(JsonCanonicalizer.parseStrict(cardJson)).get(0);
            String signingInput = encodedHeader + "." + encode(payload);
            // Signed directly over this header's JWS Signing Input, so the header can be anything.
            JsonWebSignatureAlgorithm algorithm = AlgorithmFactoryFactory.getInstance().getJwsAlgorithmFactory()
                    .getAlgorithm(alg);
            byte[] signature = algorithm.sign(algorithm.prepareForSign(key.getPrivateKey(), new ProviderContext()),
                    signingInput.getBytes(StandardCharsets.US_ASCII));
            return withSignature(cardJson, encodedHeader, encode(signature));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Appends a {@code signatures[]} entry with these exact values. */
    public static String withSignature(String cardJson, String protectedValue, String signatureValue) {
        try {
            ObjectNode card = (ObjectNode) MAPPER.readTree(cardJson);
            ArrayNode signatures = card.has("signatures") ? (ArrayNode) card.get("signatures")
                    : card.putArray("signatures");
            ObjectNode entry = signatures.addObject();
            entry.put("protected", protectedValue);
            entry.put("signature", signatureValue);
            return MAPPER.writeValueAsString(card);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Base64url of the JSON, for hand-made protected headers. */
    public static String encodeHeader(String headerJson) {
        return encode(headerJson.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
