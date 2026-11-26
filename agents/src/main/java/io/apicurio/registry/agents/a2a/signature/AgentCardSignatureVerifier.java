package io.apicurio.registry.agents.a2a.signature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.agents.signature.AgentCardSigningInput;
import io.apicurio.registry.agents.signature.JsonCanonicalizer;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Verifies the signatures of an A2A Agent Card (A2A v1.0, section 8.4): each entry of {@code signatures[]}
 * is a JWS (RFC 7515) with a detached payload, the RFC 8785 canonical form of the card without
 * {@code signatures} (see {@link AgentCardSigningInput}). The JWS Signing Input is
 * {@code protected + "." + BASE64URL(payload)}.
 * <p>
 * The protected header must carry {@code alg}, {@code typ: "JOSE"} and {@code kid}, and no {@code crit}.
 * {@code alg} must be one of the allowed algorithms, so {@code none} and HMAC algorithms never verify.
 * The unprotected {@code header} is ignored: nothing in it is trusted.
 * </p>
 * <p>
 * This class does no I/O; keys come from the {@link KeyLookup} passed in. Outcomes name the claimed
 * {@code kid} and a reason, never key material or signature bytes.
 * </p>
 */
public final class AgentCardSignatureVerifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern BASE64URL = Pattern.compile("^[A-Za-z0-9_-]+$");

    /**
     * Finds the trusted public keys for a signature.
     */
    @FunctionalInterface
    public interface KeyLookup {

        /**
         * @param kid the {@code kid} of the protected header
         * @param jku the {@code jku} of the protected header, or {@code null}
         * @return the trusted keys with this {@code kid}, possibly empty
         */
        List<JsonWebKey> keys(String kid, String jku);
    }

    /**
     * Why a signature did not verify.
     */
    public enum Failure {
        MALFORMED("malformed signature entry or protected header"),
        MISSING_ALG("missing 'alg' header"),
        DISALLOWED_ALG("algorithm not allowed"),
        MISSING_TYP("missing or unsupported 'typ' header, 'JOSE' is required"),
        MISSING_KID("missing 'kid' header"),
        UNSUPPORTED_CRIT("unsupported 'crit' header"),
        UNKNOWN_KEY("unknown key"),
        INVALID_SIGNATURE("invalid signature");

        private final String description;

        Failure(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * The outcome of one entry of {@code signatures[]}.
     *
     * @param index the position of the entry
     * @param kid the claimed {@code kid}, or {@code null} if there is none
     * @param failure why it did not verify, or {@code null} if it verified
     */
    public record SignatureOutcome(int index, String kid, Failure failure) {

        public boolean verified() {
            return failure == null;
        }
    }

    /**
     * The outcome of verifying a card.
     *
     * @param signed whether the card carries a non-empty {@code signatures} member
     * @param canonicalizable whether the card could be canonicalized; when not, no signature verifies
     * @param outcomes the outcome of each signature, in order
     */
    public record Result(boolean signed, boolean canonicalizable, List<SignatureOutcome> outcomes) {

        /**
         * @return the distinct {@code kid}s of the signatures that verified, in order
         */
        public List<String> verifiedKids() {
            Set<String> kids = new LinkedHashSet<>();
            for (SignatureOutcome outcome : outcomes) {
                if (outcome.verified()) {
                    kids.add(outcome.kid());
                }
            }
            return List.copyOf(kids);
        }

        public boolean anyVerified() {
            return outcomes.stream().anyMatch(SignatureOutcome::verified);
        }
    }

    private AgentCardSignatureVerifier() {
    }

    /**
     * @param cardJson the Agent Card as stored
     * @param keys where to find trusted keys
     * @param allowedAlgorithms the JWS algorithms accepted
     */
    public static Result verify(String cardJson, KeyLookup keys, Set<String> allowedAlgorithms) {
        JsonNode card;
        try {
            card = JsonCanonicalizer.parseStrict(cardJson);
        } catch (JsonCanonicalizer.CanonicalizationException e) {
            return new Result(isSignedLenient(cardJson), false, List.of());
        }
        JsonNode signatures = card.path(AgentCardSigningInput.SIGNATURES);
        if (!isSigned(signatures)) {
            return new Result(false, true, List.of());
        }
        if (!signatures.isArray()) {
            return new Result(true, true, List.of(new SignatureOutcome(0, null, Failure.MALFORMED)));
        }

        List<String> encodedPayloads = new ArrayList<>();
        try {
            for (byte[] payload : AgentCardSigningInput.payloads(card)) {
                encodedPayloads.add(Base64.getUrlEncoder().withoutPadding().encodeToString(payload));
            }
        } catch (JsonCanonicalizer.CanonicalizationException e) {
            return new Result(true, false, List.of());
        }

        List<SignatureOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < signatures.size(); i++) {
            outcomes.add(verifyEntry(i, signatures.get(i), encodedPayloads, keys, allowedAlgorithms));
        }
        return new Result(true, true, Collections.unmodifiableList(outcomes));
    }

    private static boolean isSigned(JsonNode signatures) {
        return !signatures.isMissingNode() && !signatures.isNull()
                && !(signatures.isArray() && signatures.isEmpty());
    }

    private static boolean isSignedLenient(String cardJson) {
        try {
            return isSigned(MAPPER.readTree(cardJson).path(AgentCardSigningInput.SIGNATURES));
        } catch (Exception e) {
            return false;
        }
    }

    private static SignatureOutcome verifyEntry(int index, JsonNode entry, List<String> encodedPayloads,
            KeyLookup keys, Set<String> allowedAlgorithms) {
        JsonNode protectedNode = entry.path("protected");
        JsonNode signatureNode = entry.path("signature");
        if (!protectedNode.isTextual() || !signatureNode.isTextual()
                || !BASE64URL.matcher(protectedNode.textValue()).matches()
                || !BASE64URL.matcher(signatureNode.textValue()).matches()) {
            return new SignatureOutcome(index, null, Failure.MALFORMED);
        }
        String encodedHeader = protectedNode.textValue();
        JsonNode header;
        try {
            header = JsonCanonicalizer.parseStrict(
                    new String(Base64.getUrlDecoder().decode(encodedHeader), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return new SignatureOutcome(index, null, Failure.MALFORMED);
        }
        if (!header.isObject()) {
            return new SignatureOutcome(index, null, Failure.MALFORMED);
        }

        JsonNode kidNode = header.path("kid");
        String kid = kidNode.isTextual() && !kidNode.textValue().isBlank() ? kidNode.textValue() : null;
        JsonNode algNode = header.path("alg");
        if (!algNode.isTextual() || algNode.textValue().isBlank()) {
            return new SignatureOutcome(index, kid, Failure.MISSING_ALG);
        }
        String alg = algNode.textValue();
        if (!allowedAlgorithms.contains(alg)) {
            return new SignatureOutcome(index, kid, Failure.DISALLOWED_ALG);
        }
        JsonNode typ = header.path("typ");
        if (!typ.isTextual() || !"JOSE".equalsIgnoreCase(typ.textValue())) {
            return new SignatureOutcome(index, kid, Failure.MISSING_TYP);
        }
        if (kid == null) {
            return new SignatureOutcome(index, null, Failure.MISSING_KID);
        }
        if (header.has("crit")) {
            return new SignatureOutcome(index, kid, Failure.UNSUPPORTED_CRIT);
        }
        JsonNode jkuNode = header.path("jku");
        String jku = jkuNode.isTextual() ? jkuNode.textValue() : null;

        List<PublicJsonWebKey> candidates = new ArrayList<>();
        for (JsonWebKey key : keys.keys(kid, jku)) {
            if (key instanceof PublicJsonWebKey publicKey && kid.equals(key.getKeyId())
                    && (key.getUse() == null || "sig".equals(key.getUse()))
                    && (key.getAlgorithm() == null || alg.equals(key.getAlgorithm()))) {
                candidates.add(publicKey);
            }
        }
        if (candidates.isEmpty()) {
            return new SignatureOutcome(index, kid, Failure.UNKNOWN_KEY);
        }

        for (PublicJsonWebKey key : candidates) {
            for (String encodedPayload : encodedPayloads) {
                if (verifies(encodedHeader + "." + encodedPayload + "." + signatureNode.textValue(), key, alg)) {
                    return new SignatureOutcome(index, kid, null);
                }
            }
        }
        return new SignatureOutcome(index, kid, Failure.INVALID_SIGNATURE);
    }

    private static boolean verifies(String compactSerialization, PublicJsonWebKey key, String alg) {
        try {
            JsonWebSignature jws = new JsonWebSignature();
            // Only the algorithm already checked against the allow-list.
            jws.setAlgorithmConstraints(new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, alg));
            jws.setCompactSerialization(compactSerialization);
            jws.setKey(key.getPublicKey());
            return jws.verifySignature();
        } catch (JoseException | RuntimeException e) {
            // A key of the wrong type or size for the algorithm, or a malformed signature value.
            return false;
        }
    }
}
