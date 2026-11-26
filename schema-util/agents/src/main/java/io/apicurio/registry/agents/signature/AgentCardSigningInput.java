package io.apicurio.registry.agents.signature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The payloads an A2A Agent Card signature (A2A v1.0, section 8.4) may have been computed over: the RFC
 * 8785 canonical form of the card without its {@code signatures} member.
 * <p>
 * Section 8.4 also asks signers to drop "properties with default values" first, following Protocol
 * Buffer field presence, which cannot be reproduced exactly from JSON without the card's Protocol Buffer
 * schema. Two forms are therefore accepted, both derived only from the stored card:
 * <ol>
 * <li>the card exactly as stored, without {@code signatures};</li>
 * <li>the same with empty strings, empty arrays, empty objects and {@code null} values removed
 * recursively, which is what the reference a2a-python SDK ({@code a2a.utils.signing}) signs and
 * verifies, and which reproduces the canonical example of section 8.4.1.</li>
 * </ol>
 * Neither form removes or adds a non-empty value, so a signature over either one covers every non-empty
 * value a client reads from the stored card. Accepting the second form means that empty values can be
 * added to or removed from a signed card without breaking its signature; the reference SDK has the same
 * property, and Protocol Buffer readers treat an empty value as an absent one.
 */
public final class AgentCardSigningInput {

    /** The Agent Card member holding the signatures, excluded from the signed content. */
    public static final String SIGNATURES = "signatures";

    private AgentCardSigningInput() {
    }

    /**
     * @param card the parsed Agent Card, which must be a JSON object
     * @return the distinct canonical payloads, as UTF-8 bytes, the exact form first
     * @throws JsonCanonicalizer.CanonicalizationException if the card cannot be canonicalized
     */
    public static List<byte[]> payloads(JsonNode card) {
        if (!card.isObject()) {
            throw new JsonCanonicalizer.CanonicalizationException("an Agent Card must be a JSON object");
        }
        ObjectNode unsigned = ((ObjectNode) card).deepCopy();
        unsigned.remove(SIGNATURES);
        byte[] exact = JsonCanonicalizer.canonicalize(unsigned);

        List<byte[]> payloads = new ArrayList<>();
        payloads.add(exact);
        JsonNode cleaned = withoutEmptyValues(unsigned, 1);
        byte[] withoutEmpty = JsonCanonicalizer.canonicalize(
                cleaned == null ? JsonNodeFactory.instance.objectNode() : cleaned);
        if (!Arrays.equals(exact, withoutEmpty)) {
            payloads.add(withoutEmpty);
        }
        return payloads;
    }

    /**
     * @return the value with empty strings, arrays, objects and nulls removed, or {@code null} if nothing
     *         is left of it
     */
    static JsonNode withoutEmptyValues(JsonNode node, int depth) {
        if (node.isContainerNode() && depth > JsonCanonicalizer.MAX_DEPTH) {
            throw new JsonCanonicalizer.CanonicalizationException(
                    "nesting deeper than " + JsonCanonicalizer.MAX_DEPTH);
        }
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = withoutEmptyValues(field.getValue(), depth + 1);
                if (value != null) {
                    result.set(field.getKey(), value);
                }
            }
            return result.isEmpty() ? null : result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : node) {
                JsonNode value = withoutEmptyValues(item, depth + 1);
                if (value != null) {
                    result.add(value);
                }
            }
            return result.isEmpty() ? null : result;
        }
        if (node.isNull() || (node.isTextual() && node.textValue().isEmpty())) {
            return null;
        }
        return node;
    }
}
