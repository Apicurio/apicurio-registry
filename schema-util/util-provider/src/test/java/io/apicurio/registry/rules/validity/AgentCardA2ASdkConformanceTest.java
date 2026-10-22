package io.apicurio.registry.rules.validity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.a2aproject.sdk.spec.APIKeySecurityScheme;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.HTTPAuthSecurityScheme;
import org.a2aproject.sdk.spec.SecurityRequirement;
import org.a2aproject.sdk.spec.SecurityScheme;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conformance test that feeds Apicurio's own Agent Card fixtures through the *official* A2A Java
 * SDK's {@code AgentCard} model ({@code org.a2aproject.sdk:a2a-java-sdk-spec}), rather than only
 * validating them against Apicurio's own hand-maintained
 * {@code a2a-agent-card-schema.json}.
 * <p>
 * <b>Why this dependency exists:</b> {@code a2a-java-sdk-spec} is added purely as a test-scope
 * conformance oracle to catch schema drift between Apicurio's Agent Card output and the shape
 * that real A2A clients (built on {@code org.a2aproject.sdk}) actually expect. It must never be
 * used as (or mistaken for) a production dependency: Apicurio's own {@code AgentCard} model is,
 * and remains, generated from {@code common/src/main/resources/META-INF/openapi.json} - the
 * single source of truth shared with the Go, Java (kiota), Python, and TypeScript SDKs. See
 * <a href="https://github.com/a2aproject/a2a-java/issues/1121">a2aproject/a2a-java#1121</a> for
 * the exact failure mode this test guards against: an Agent Card that satisfies Apicurio's own
 * schema but is silently misread (or rejected) by the reference SDK.
 * <p>
 * Note {@code a2a-java-sdk-spec}'s {@link SecurityScheme} is a sealed interface with no built-in
 * Gson polymorphic adapter in the {@code spec} module itself (the reference SDK's own adapter for
 * it lives in the heavier {@code jsonrpc-common} module, and uses a different, JSON-RPC-oriented
 * wire encoding not relevant here). This test registers a minimal adapter dispatching strictly on
 * the plain {@code type} discriminator field that Apicurio's Agent Card JSON actually uses, so
 * that a real structural mismatch (wrong field names, wrong required fields) in the reference
 * SDK's own record classes still fails this test loudly.
 */
public class AgentCardA2ASdkConformanceTest extends ArtifactUtilProviderTestBase {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(SecurityScheme.class, new SecuritySchemeAdapter())
            .registerTypeAdapter(SecurityRequirement.class, new SecurityRequirementAdapter())
            .create();

    @ParameterizedTest
    @ValueSource(strings = {
            "agentcard-valid.json",
            "agentcard-minimal.json",
            "agentcard-full.json",
            "agentcard-uppercase-scheme-url.json",
            "agentcard-ipv6-url.json"
    })
    public void testAgentCardParsesWithReferenceSdk(String resourceName) throws IOException {
        String json = resourceToString(resourceName);

        // The whole point of this test: if the reference SDK's own AgentCard model rejects
        // Apicurio's output, this must fail loudly (a JsonSyntaxException propagating out),
        // not be swallowed.
        AgentCard card = GSON.fromJson(json, AgentCard.class);

        Assertions.assertNotNull(card, "Reference SDK failed to produce an AgentCard from: " + resourceName);
        Assertions.assertNotNull(card.name());
        Assertions.assertNotNull(card.version());

        // supportedInterfaces: every fixture must round-trip at least one interface, with both
        // the protocol binding and URL preserved exactly.
        Assertions.assertNotNull(card.supportedInterfaces());
        Assertions.assertFalse(card.supportedInterfaces().isEmpty(),
                "supportedInterfaces must not be empty in: " + resourceName);
        for (AgentInterface iface : card.supportedInterfaces()) {
            Assertions.assertNotNull(iface.protocolBinding(), "protocolBinding must round-trip in: " + resourceName);
            Assertions.assertNotNull(iface.url(), "url must round-trip in: " + resourceName);
        }

        // skills[].description/tags: every skill must round-trip its description and tags.
        Assertions.assertNotNull(card.skills());
        Assertions.assertFalse(card.skills().isEmpty(), "skills must not be empty in: " + resourceName);
        for (AgentSkill skill : card.skills()) {
            Assertions.assertNotNull(skill.description(),
                    "skill description must round-trip in: " + resourceName);
            Assertions.assertNotNull(skill.tags(), "skill tags must round-trip in: " + resourceName);
            Assertions.assertFalse(skill.tags().isEmpty(), "skill tags must not be empty in: " + resourceName);
        }
    }

    @org.junit.jupiter.api.Test
    public void testSecuritySchemesRoundTripThroughReferenceSdk() throws IOException {
        String json = resourceToString("agentcard-full.json");
        AgentCard card = GSON.fromJson(json, AgentCard.class);

        Assertions.assertNotNull(card.securitySchemes());
        Map<String, SecurityScheme> schemes = card.securitySchemes();

        Assertions.assertTrue(schemes.get("bearer") instanceof HTTPAuthSecurityScheme);
        HTTPAuthSecurityScheme bearer = (HTTPAuthSecurityScheme) schemes.get("bearer");
        Assertions.assertEquals("Bearer", bearer.scheme());
        Assertions.assertEquals("JWT", bearer.bearerFormat());

        Assertions.assertTrue(schemes.get("apikey") instanceof APIKeySecurityScheme);
        APIKeySecurityScheme apiKey = (APIKeySecurityScheme) schemes.get("apikey");
        Assertions.assertEquals("X-API-Key", apiKey.name());
        Assertions.assertEquals(APIKeySecurityScheme.Location.HEADER, apiKey.location());
    }

    /**
     * Minimal Gson adapter for the reference SDK's sealed {@link SecurityScheme} interface,
     * dispatching on the plain {@code type} discriminator field Apicurio's Agent Card JSON uses
     * (as opposed to the reference SDK's own wrapper-object encoding used internally for its
     * JSON-RPC transport). Only used by this conformance test.
     */
    private static class SecuritySchemeAdapter extends TypeAdapter<SecurityScheme> {

        private final Gson delegate = new GsonBuilder()
                .registerTypeAdapter(APIKeySecurityScheme.Location.class, new TypeAdapter<APIKeySecurityScheme.Location>() {
                    @Override
                    public void write(JsonWriter out, APIKeySecurityScheme.Location value) throws IOException {
                        out.value(value == null ? null : value.asString());
                    }

                    @Override
                    public APIKeySecurityScheme.Location read(JsonReader in) throws IOException {
                        return APIKeySecurityScheme.Location.fromString(in.nextString());
                    }
                })
                .create();

        @Override
        public void write(JsonWriter out, SecurityScheme value) throws IOException {
            // Not needed for this conformance test, which only deserializes Apicurio's output.
            throw new UnsupportedOperationException(
                    "SecuritySchemeAdapter is read-only; it exists only to deserialize Agent Card JSON");
        }

        @Override
        public SecurityScheme read(JsonReader in) throws IOException {
            com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseReader(in).getAsJsonObject();
            String type = jsonObject.has("type") ? jsonObject.get("type").getAsString() : null;
            if (type == null) {
                throw new JsonSyntaxException("SecurityScheme JSON object must have a 'type' discriminator field");
            }
            return switch (type) {
                case "apiKey" -> delegate.fromJson(jsonObject, APIKeySecurityScheme.class);
                case "httpAuth" -> delegate.fromJson(jsonObject, HTTPAuthSecurityScheme.class);
                default -> throw new JsonSyntaxException(
                        "Unsupported SecurityScheme type for conformance test: " + type);
            };
        }
    }

    /**
     * Minimal Gson adapter for {@link SecurityRequirement}, whose single {@code schemes} record
     * component is an implementation detail of the reference SDK's own JSON-RPC wire encoding
     * (which nests scheme names under a {@code schemes} object, each with a {@code list} field,
     * mirroring the protobuf representation). Apicurio's Agent Card JSON follows the plain,
     * OpenAPI-style flat encoding instead: each {@code securityRequirements} array entry is
     * directly a scheme-name-to-scopes map, with no wrapper. This adapter bridges that so genuine
     * mismatches in {@code supportedInterfaces}, {@code securitySchemes}, and {@code skills} (the
     * fields this conformance test actually asserts on) are what surface, not an unrelated
     * artifact of the reference SDK's internal record shape.
     */
    private static class SecurityRequirementAdapter extends TypeAdapter<SecurityRequirement> {

        @Override
        public void write(JsonWriter out, SecurityRequirement value) throws IOException {
            throw new UnsupportedOperationException(
                    "SecurityRequirementAdapter is read-only; it exists only to deserialize Agent Card JSON");
        }

        @Override
        public SecurityRequirement read(JsonReader in) throws IOException {
            com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseReader(in).getAsJsonObject();
            Map<String, List<String>> schemes = new LinkedHashMap<>();
            for (String schemeName : jsonObject.keySet()) {
                List<String> scopes = new java.util.ArrayList<>();
                jsonObject.getAsJsonArray(schemeName).forEach(el -> scopes.add(el.getAsString()));
                schemes.put(schemeName, scopes);
            }
            return new SecurityRequirement(schemes);
        }
    }
}