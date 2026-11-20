package io.apicurio.registry.content.dereference;

import io.apicurio.registry.asyncapi.content.dereference.AsyncApiDereferencer;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.asyncapi.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.types.ContentTypes;
import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.util.ContentTypeUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static io.apicurio.registry.utils.tests.TestUtils.normalizeMultiLineString;

public class AsyncApiContentDereferencerTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testRewriteReferences() {
        TypedContent content = resourceToTypedContentHandle("asyncapi-to-rewrite.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        TypedContent modifiedContent = dereferencer.rewriteReferences(content,
                Map.of("./TradeKey.avsc", "https://www.example.org/schemas/TradeKey.avsc",
                        "./common-types.json#/components/schemas/User",
                        "https://www.example.org/schemas/common-types.json#/components/schemas/User"));

        ReferenceFinder finder = new AsyncApiReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference(
                "https://www.example.org/schemas/common-types.json#/components/schemas/User")));
        Assertions.assertTrue(externalReferences
                .contains(new JsonPointerExternalReference("https://www.example.org/schemas/TradeKey.avsc")));
    }

    @Test
    public void testRewriteReferencesPreservesYamlFormat() {
        TypedContent content = resourceToTypedContentHandle("asyncapi-to-rewrite.yaml");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        TypedContent modifiedContent = dereferencer.rewriteReferences(content,
                Map.of("./TradeKey.avsc", "https://www.example.org/schemas/TradeKey.avsc",
                        "./common-types.json#/components/schemas/User",
                        "https://www.example.org/schemas/common-types.json#/components/schemas/User"));

        // Verify that the content type is still YAML
        Assertions.assertEquals(ContentTypes.APPLICATION_YAML, modifiedContent.getContentType());

        // Verify that the content is valid YAML (not JSON)
        String contentString = modifiedContent.getContent().content();
        Assertions.assertFalse(contentString.trim().startsWith("{"),
                "Content should be YAML, not JSON");

        // Verify that references were rewritten by checking the content
        ReferenceFinder finder = new AsyncApiReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference(
                "https://www.example.org/schemas/common-types.json#/components/schemas/User")));
        Assertions.assertTrue(externalReferences
                .contains(new JsonPointerExternalReference("https://www.example.org/schemas/TradeKey.avsc")));
    }

    @Test
    public void testRewriteReferencesPreservesJsonFormat() {
        TypedContent content = resourceToTypedContentHandle("asyncapi-to-rewrite.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        TypedContent modifiedContent = dereferencer.rewriteReferences(content,
                Map.of("./TradeKey.avsc", "https://www.example.org/schemas/TradeKey.avsc",
                        "./common-types.json#/components/schemas/User",
                        "https://www.example.org/schemas/common-types.json#/components/schemas/User"));

        // Verify that the content type is still JSON
        Assertions.assertEquals(ContentTypes.APPLICATION_JSON, modifiedContent.getContentType());

        // Verify that the content is valid JSON (starts with {)
        String contentString = modifiedContent.getContent().content();
        Assertions.assertTrue(contentString.trim().startsWith("{"),
                "Content should be JSON");

        // Verify that references were rewritten
        ReferenceFinder finder = new AsyncApiReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference(
                "https://www.example.org/schemas/common-types.json#/components/schemas/User")));
        Assertions.assertTrue(externalReferences
                .contains(new JsonPointerExternalReference("https://www.example.org/schemas/TradeKey.avsc")));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document with a reference to an external AsyncAPI 3.0 schema.
     */
    @Test
    public void testDereferenceAsyncApi30ToAsyncApi30() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-asyncapi.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map.of(
                "http://types.example.org/asyncapi30-common-types.json#/components/schemas/User",
                TypedContent.create(resourceToContentHandle("asyncapi30-common-types.json"),
                        ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-asyncapi30-asyncapi.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document with a reference to an Avro schema.
     * The Avro schema should be wrapped in a MultiFormatSchema with the versioned Avro schemaFormat.
     */
    @Test
    public void testDereferenceAsyncApi30ToAvro() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-avro.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map.of("http://schemas.example.org/user-event.avsc",
                TypedContent.create(resourceToContentHandle("user-event.avsc"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-asyncapi30-avro.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));

        Assertions.assertEquals("application/vnd.apache.avro+json;version=1.9.0",
                schemaFormatOf(modifiedContent, "UserEvent"));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document whose payload declares a non-default Avro version.
     * The declared schemaFormat should be kept on the inlined schema rather than overwritten.
     */
    @Test
    public void testDereferenceAsyncApi30ToAvroKeepsDeclaredSchemaFormat() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-avro-versioned-payload.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map.of("http://schemas.example.org/user-event.avsc",
                TypedContent.create(resourceToContentHandle("user-event.avsc"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);

        String expectedContent = resourceToString(
                "expected-testDereference-asyncapi30-avro-versioned-payload.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));

        Assertions.assertEquals("application/vnd.apache.avro+json;version=1.11.0",
                schemaFormatOf(modifiedContent, "UserEvent"));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document with a reference to a proto3 schema.
     * The Protobuf schema should be wrapped in a MultiFormatSchema with the proto3 schemaFormat.
     */
    @Test
    public void testDereferenceAsyncApi30ToProtobuf() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-protobuf.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map
                .of("http://schemas.example.org/user-profile.proto", TypedContent
                        .create(resourceToContentHandle("user-profile.proto"), "application/x-protobuf"));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-asyncapi30-protobuf.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));

        Assertions.assertEquals("application/vnd.google.protobuf;version=3",
                schemaFormatOf(modifiedContent, "UserProfile"));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document with a reference to a proto2 schema.
     * The schemaFormat version should follow the schema's syntax statement.
     */
    @Test
    public void testDereferenceAsyncApi30ToProtobuf2() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-protobuf-proto2.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map
                .of("http://schemas.example.org/user-settings.proto", TypedContent
                        .create(resourceToContentHandle("user-settings.proto"), "application/x-protobuf"));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-asyncapi30-protobuf-proto2.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));

        Assertions.assertEquals("application/vnd.google.protobuf;version=2",
                schemaFormatOf(modifiedContent, "UserSettings"));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document whose payload declares a Protobuf schemaFormat.
     * The declared schemaFormat should win over the version in the schema's syntax statement.
     */
    @Test
    public void testDereferenceAsyncApi30ToProtobufKeepsDeclaredSchemaFormat() throws Exception {
        ContentHandle content = resourceToContentHandle(
                "asyncapi30-to-deref-protobuf-declared-format.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map
                .of("http://schemas.example.org/user-profile.proto", TypedContent
                        .create(resourceToContentHandle("user-profile.proto"), "application/x-protobuf"));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString(
                "expected-testDereference-asyncapi30-protobuf-declared-format.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));

        Assertions.assertEquals("application/vnd.google.protobuf;version=2",
                schemaFormatOf(modifiedContent, "UserProfile"));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document referencing a Protobuf schema with no syntax statement.
     * The schema should be treated as proto2, ignoring syntax statements inside comments.
     */
    @Test
    public void testDereferenceAsyncApi30ToProtobufWithoutSyntaxStatement() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-protobuf-legacy.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map
                .of("http://schemas.example.org/legacy-record.proto", TypedContent
                        .create(resourceToContentHandle("legacy-record.proto"), "application/x-protobuf"));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);

        Assertions.assertEquals("application/vnd.google.protobuf;version=2",
                schemaFormatOf(modifiedContent, "LegacyRecord"));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document referencing a Protobuf schema that uses an edition.
     * The schema should be treated as proto3.
     */
    @Test
    public void testDereferenceAsyncApi30ToProtobufEdition() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-protobuf-edition.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map
                .of("http://schemas.example.org/edition-record.proto", TypedContent
                        .create(resourceToContentHandle("edition-record.proto"), "application/x-protobuf"));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);

        Assertions.assertEquals("application/vnd.google.protobuf;version=3",
                schemaFormatOf(modifiedContent, "EditionRecord"));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document whose payload declares a non-Avro schemaFormat
     * over an Avro reference. The inlined schema should get the Avro schemaFormat, not the declared one.
     */
    @Test
    public void testDereferenceAsyncApi30ToAvroIgnoresForeignSchemaFormat() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-avro-foreign-format.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map.of("http://schemas.example.org/user-event.avsc",
                TypedContent.create(resourceToContentHandle("user-event.avsc"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);

        Assertions.assertEquals("application/vnd.apache.avro+json;version=1.9.0",
                schemaFormatOf(modifiedContent, "UserEvent"));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document with references to JSON Schema definitions.
     * The JSON Schema definitions should be properly inlined.
     */
    @Test
    public void testDereferenceAsyncApi30ToJsonSchema() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-jsonschema.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map.of(
                "http://schemas.example.org/common-schemas.json#/definitions/Address",
                TypedContent.create(resourceToContentHandle("common-schemas.json"),
                        ContentTypes.APPLICATION_JSON),
                "http://schemas.example.org/common-schemas.json#/definitions/Contact",
                TypedContent.create(resourceToContentHandle("common-schemas.json"),
                        ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-asyncapi30-jsonschema.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document whose payload declares a versionless Avro schemaFormat.
     * The versionless value is not parseable, so both the inlined schema and the payload wrapper should
     * carry the registered, versioned Avro schemaFormat.
     */
    @Test
    public void testDereferenceAsyncApi30ToAvroNormalizesVersionlessSchemaFormat() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-avro-versionless-payload.json");
        Map<String, TypedContent> resolvedReferences = Map.of("http://schemas.example.org/user-event.avsc",
                TypedContent.create(resourceToContentHandle("user-event.avsc"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = new AsyncApiDereferencer()
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);

        Assertions.assertEquals("application/vnd.apache.avro+json;version=1.9.0",
                schemaFormatOf(modifiedContent, "UserEvent"));
        Assertions.assertEquals("application/vnd.apache.avro+json;version=1.9.0",
                payloadSchemaFormatOf(modifiedContent, "UserEventMessage"));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document whose payload declares a versionless Protobuf
     * schemaFormat. The version should be inferred from the schema and applied to both the inlined schema
     * and the payload wrapper.
     */
    @Test
    public void testDereferenceAsyncApi30ToProtobufNormalizesVersionlessSchemaFormat() throws Exception {
        ContentHandle content = resourceToContentHandle(
                "asyncapi30-to-deref-protobuf-versionless-format.json");
        Map<String, TypedContent> resolvedReferences = Map
                .of("http://schemas.example.org/user-profile.proto", TypedContent
                        .create(resourceToContentHandle("user-profile.proto"), "application/x-protobuf"));
        TypedContent modifiedContent = new AsyncApiDereferencer()
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);

        Assertions.assertEquals("application/vnd.google.protobuf;version=3",
                schemaFormatOf(modifiedContent, "UserProfile"));
        Assertions.assertEquals("application/vnd.google.protobuf;version=3",
                payloadSchemaFormatOf(modifiedContent, "ProfileUpdateMessage"));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document where several messages share one Avro reference. The
     * reference is only resolved once, so a later wrapper declaring a versionless Avro schemaFormat must
     * still be normalized, while a wrapper declaring a format from another family is left untouched.
     */
    @Test
    public void testDereferenceAsyncApi30ToAvroNormalizesSharedReference() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-avro-shared-ref.json");
        Map<String, TypedContent> resolvedReferences = Map.of("http://schemas.example.org/user-event.avsc",
                TypedContent.create(resourceToContentHandle("user-event.avsc"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = new AsyncApiDereferencer()
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);

        Assertions.assertEquals("application/vnd.apache.avro+json;version=1.9.0",
                schemaFormatOf(modifiedContent, "UserEvent"));
        Assertions.assertEquals("application/vnd.apache.avro+json;version=1.9.0",
                payloadSchemaFormatOf(modifiedContent, "UserEventVersionlessMessage"));
        Assertions.assertEquals("application/schema+json;version=draft-07",
                payloadSchemaFormatOf(modifiedContent, "UserEventForeignMessage"));
    }

    /**
     * Test dereferencing an AsyncAPI 2.6 document whose message payloads reference Avro, Protobuf and JSON
     * Schema artifacts. Each referenced schema should be inlined into the payload, and messages carrying
     * Avro or Protobuf payloads should get a versioned schemaFormat.
     */
    @Test
    public void testDereferenceAsyncApi26MessagePayloads() throws Exception {
        TypedContent content = TypedContent.create(resourceToContentHandle("asyncapi26-to-deref.json"),
                ContentTypes.APPLICATION_JSON);
        Map<String, TypedContent> resolvedReferences = Map.of(
                "http://schemas.example.org/user-event.avsc",
                TypedContent.create(resourceToContentHandle("user-event.avsc"), ContentTypes.APPLICATION_JSON),
                "http://schemas.example.org/user-profile.proto",
                TypedContent.create(resourceToContentHandle("user-profile.proto"), "application/x-protobuf"),
                "http://schemas.example.org/user-settings.proto",
                TypedContent.create(resourceToContentHandle("user-settings.proto"), "application/x-protobuf"),
                "http://schemas.example.org/address.json",
                TypedContent.create(resourceToContentHandle("asyncapi26-address.json"), ContentTypes.APPLICATION_JSON),
                "http://schemas.example.org/order.json",
                TypedContent.create(resourceToContentHandle("asyncapi26-order.json"), ContentTypes.APPLICATION_JSON));

        JsonNode document = ContentTypeUtil
                .parseJsonOrYaml(new AsyncApiDereferencer().dereference(content, resolvedReferences));
        JsonNode messages = document.path("components").path("messages");

        // Avro without a declared schemaFormat: inlined, default versioned format
        JsonNode channelMessage = document.path("channels").path("userEvents").path("publish").path("message");
        Assertions.assertEquals("application/vnd.apache.avro+json;version=1.9.0",
                channelMessage.path("schemaFormat").asText());
        Assertions.assertEquals("UserEvent", channelMessage.path("payload").path("name").asText());

        // Avro with a versionless schemaFormat, inside oneOf: normalized
        JsonNode oneOfAvro = document.path("channels").path("mixedEvents").path("subscribe").path("message")
                .path("oneOf").path(1);
        Assertions.assertEquals("application/vnd.apache.avro+json;version=1.9.0",
                oneOfAvro.path("schemaFormat").asText());
        Assertions.assertEquals("record", oneOfAvro.path("payload").path("type").asText());

        // Avro with a versioned schemaFormat: kept
        Assertions.assertEquals("application/vnd.apache.avro;version=1.9.0",
                messages.path("DeclaredAvroMessage").path("schemaFormat").asText());
        Assertions.assertEquals("UserEvent",
                messages.path("DeclaredAvroMessage").path("payload").path("name").asText());

        // Protobuf: inlined as text, version inferred from the schema or kept when declared
        JsonNode profile = messages.path("ProfileMessage");
        Assertions.assertEquals("application/vnd.google.protobuf;version=3", profile.path("schemaFormat").asText());
        Assertions.assertTrue(profile.path("payload").asText().contains("message UserProfile"),
                "Protobuf payload should be inlined as text but was: " + profile.path("payload"));
        JsonNode settings = messages.path("SettingsMessage");
        Assertions.assertEquals("application/vnd.google.protobuf;version=3",
                settings.path("schemaFormat").asText());
        Assertions.assertTrue(settings.path("payload").asText().contains("message UserSettings"));

        // Self-contained JSON Schema: inlined, schemaFormat left unset (AsyncAPI default)
        JsonNode jsonSchema = messages.path("JsonSchemaMessage");
        Assertions.assertTrue(jsonSchema.path("schemaFormat").isMissingNode());
        Assertions.assertEquals("string",
                jsonSchema.path("payload").path("properties").path("street").path("type").asText());

        // JSON Schema with internal $refs, local refs and unknown refs: left untouched
        Assertions.assertEquals("http://schemas.example.org/order.json",
                messages.path("JsonSchemaWithRefsMessage").path("payload").path("$ref").asText());
        Assertions.assertEquals("#/components/schemas/Local",
                messages.path("LocalRefMessage").path("payload").path("$ref").asText());
        Assertions.assertEquals("http://schemas.example.org/missing.avsc",
                messages.path("UnknownRefMessage").path("payload").path("$ref").asText());
    }

    /**
     * Test that an edition-like string literal in a proto2 schema does not change the detected version.
     * Only the file's leading syntax/edition declaration determines the version.
     */
    @Test
    public void testDereferenceAsyncApi30ToProtobufIgnoresEditionInStringLiteral() throws Exception {
        Assertions.assertEquals("application/vnd.google.protobuf;version=2",
                dereferenceLegacyRecord("proto2-with-edition-string.proto"));
    }

    /**
     * Test that a syntax-like string literal in a schema with no syntax statement does not change the
     * detected version. Without a leading declaration the schema is proto2.
     */
    @Test
    public void testDereferenceAsyncApi30ToProtobufIgnoresSyntaxInStringLiteral() throws Exception {
        Assertions.assertEquals("application/vnd.google.protobuf;version=2",
                dereferenceLegacyRecord("proto-no-syntax-with-syntax-string.proto"));
    }

    /**
     * Dereferences the legacy-record AsyncAPI fixture against the given Protobuf resource and returns the
     * schemaFormat of the inlined schema.
     *
     * @param protoResource the Protobuf resource to resolve the reference to
     */
    private String dereferenceLegacyRecord(String protoResource) throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-protobuf-legacy.json");
        Map<String, TypedContent> resolvedReferences = Map
                .of("http://schemas.example.org/legacy-record.proto", TypedContent
                        .create(resourceToContentHandle(protoResource), "application/x-protobuf"));
        TypedContent modifiedContent = new AsyncApiDereferencer()
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        return schemaFormatOf(modifiedContent, "LegacyRecord");
    }

    /**
     * Returns the schemaFormat of a message payload under components/messages.
     *
     * @param content the dereferenced content
     * @param messageName the message name
     */
    private String payloadSchemaFormatOf(TypedContent content, String messageName) throws Exception {
        JsonNode document = ContentTypeUtil.parseJsonOrYaml(content);
        JsonNode schemaFormat = document.path("components").path("messages").path(messageName)
                .path("payload").get("schemaFormat");
        Assertions.assertNotNull(schemaFormat, "No schemaFormat on the payload of " + messageName);
        return schemaFormat.asText();
    }

    /**
     * Returns the schemaFormat of an inlined schema under components/schemas.
     *
     * @param content the dereferenced content
     * @param schemaName the inlined schema name
     */
    private String schemaFormatOf(TypedContent content, String schemaName) throws Exception {
        JsonNode document = ContentTypeUtil.parseJsonOrYaml(content);
        JsonNode schema = document.path("components").path("schemas").path(schemaName);
        Assertions.assertFalse(schema.isMissingNode(),
                "No components/schemas entry named " + schemaName + " in: " + document);
        JsonNode schemaFormat = schema.get("schemaFormat");
        Assertions.assertNotNull(schemaFormat, "No schemaFormat on components/schemas/" + schemaName);
        return schemaFormat.asText();
    }

}
