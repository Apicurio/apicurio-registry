package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.json.content.dereference.JsonSchemaDereferencer;
import io.apicurio.registry.json.content.refs.JsonSchemaReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static io.apicurio.registry.utils.tests.TestUtils.normalizeMultiLineString;

public class JsonSchemaContentDereferencerTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testRewriteReferences() {
        TypedContent content = resourceToTypedContentHandle("json-schema-to-rewrite.json");
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();
        TypedContent modifiedContent = dereferencer.rewriteReferences(content,
                Map.of("./address.json", "https://www.example.org/schemas/address.json", "./ssn.json",
                        "https://www.example.org/schemas/ssn.json"));

        ReferenceFinder finder = new JsonSchemaReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences
                .contains(new JsonPointerExternalReference("https://www.example.org/schemas/address.json")));
        Assertions.assertTrue(externalReferences
                .contains(new JsonPointerExternalReference("https://www.example.org/schemas/ssn.json")));
    }

    @Test
    public void testDereferenceObjectLevel() throws Exception {
        TypedContent content = TypedContent.create(
                resourceToContentHandle("json-schema-to-deref-object-level.json"),
                ContentTypes.APPLICATION_JSON);
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();
        // Note: order is important. The JSON schema dereferencer needs to convert the ContentHandle Map
        // to a JSONSchema map. So it *must* resolve the leaves of the dependency tree before the branches.
        Map<String, TypedContent> resolvedReferences = new LinkedHashMap<>();
        resolvedReferences.put("types/city/qualification.json", TypedContent.create(
                resourceToContentHandle("types/city/qualification.json"), ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("city/qualification.json", TypedContent.create(
                resourceToContentHandle("types/city/qualification.json"), ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("identifier/qualification.json",
                TypedContent.create(resourceToContentHandle("types/identifier/qualification.json"),
                        ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("types/all-types.json#/definitions/City", TypedContent
                .create(resourceToContentHandle("types/all-types.json"), ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("types/all-types.json#/definitions/Identifier", TypedContent
                .create(resourceToContentHandle("types/all-types.json"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer.dereference(content, resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-object-level-json.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    @Test
    public void testDereferencePropertyLevel() throws Exception {
        TypedContent content = TypedContent.create(
                resourceToContentHandle("json-schema-to-deref-property-level.json"),
                ContentTypes.APPLICATION_JSON);
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();
        // Note: order is important. The JSON schema dereferencer needs to convert the ContentHandle Map
        // to a JSONSchema map. So it *must* resolve the leaves of the dependency tree before the branches.
        Map<String, TypedContent> resolvedReferences = new LinkedHashMap<>();
        resolvedReferences.put("types/city/qualification.json", TypedContent.create(
                resourceToContentHandle("types/city/qualification.json"), ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("city/qualification.json", TypedContent.create(
                resourceToContentHandle("types/city/qualification.json"), ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("identifier/qualification.json",
                TypedContent.create(resourceToContentHandle("types/identifier/qualification.json"),
                        ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("types/all-types.json#/definitions/City/properties/name", TypedContent
                .create(resourceToContentHandle("types/all-types.json"), ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("types/all-types.json#/definitions/Identifier", TypedContent
                .create(resourceToContentHandle("types/all-types.json"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer.dereference(content, resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-property-level-json.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    // Resolves multiple $refs using a single reference to a file with multiple definitions
    @Test
    public void testReferenceSingleFile() throws Exception {
        TypedContent content = TypedContent.create(
                resourceToContentHandle("json-schema-to-deref-property-level.json"),
                ContentTypes.APPLICATION_JSON);
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();
        // Note: order is important. The JSON schema dereferencer needs to convert the ContentHandle Map
        // to a JSONSchema map. So it *must* resolve the leaves of the dependency tree before the branches.
        Map<String, TypedContent> resolvedReferences = new LinkedHashMap<>();
        resolvedReferences.put("types/city/qualification.json", TypedContent.create(
                resourceToContentHandle("types/city/qualification.json"), ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("city/qualification.json", TypedContent.create(
                resourceToContentHandle("types/city/qualification.json"), ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("identifier/qualification.json",
                TypedContent.create(resourceToContentHandle("types/identifier/qualification.json"),
                        ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("types/all-types.json", TypedContent
                .create(resourceToContentHandle("types/all-types.json"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer.dereference(content, resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-property-level-json.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    @Test
    public void testMultipleRefsUseSingleFile() throws Exception {
        TypedContent content = TypedContent.create(
                resourceToContentHandle("json-schema-to-deref-property-level.json"),
                ContentTypes.APPLICATION_JSON);
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();
        // Note: order is important. The JSON schema dereferencer needs to convert the ContentHandle Map
        // to a JSONSchema map. So it *must* resolve the leaves of the dependency tree before the branches.
        Map<String, TypedContent> resolvedReferences = new LinkedHashMap<>();
        resolvedReferences.put("types/city/qualification.json", TypedContent.create(
                resourceToContentHandle("types/city/qualification.json"), ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("city/qualification.json", TypedContent.create(
                resourceToContentHandle("types/city/qualification.json"), ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("identifier/qualification.json",
                TypedContent.create(resourceToContentHandle("types/identifier/qualification.json"),
                        ContentTypes.APPLICATION_JSON));
        resolvedReferences.put("types/all-types.json", TypedContent
                .create(resourceToContentHandle("types/all-types.json"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer.dereference(content, resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-property-level-json.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    @Test
    public void testDerefAllOf() throws Exception {
        TypedContent content = TypedContent.create(resourceToContentHandle("order.json"),
                ContentTypes.APPLICATION_JSON);
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();

        Map<String, TypedContent> resolvedReferences = new LinkedHashMap<>();

        resolvedReferences.put("customer.json",
                TypedContent.create(resourceToContentHandle("customer.json"), ContentTypes.APPLICATION_JSON));

        TypedContent modifiedContent = dereferencer.dereference(content, resolvedReferences);

        String expectedContent = resourceToString("expected-order-deref.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    @Test
    public void testRewriteAllOfReferences() {
        TypedContent content = resourceToTypedContentHandle("order.json");
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();
        TypedContent modifiedContent = dereferencer.rewriteReferences(content,
                Map.of("customer.json", "https://www.example.org/schemas/customer.json"));

        ReferenceFinder finder = new JsonSchemaReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences
                .contains(new JsonPointerExternalReference("https://www.example.org/schemas/customer.json")));
    }

    /**
     * Test that dereferencing works when the schema is missing the $id property.
     * This verifies the fix for issue #6944 where missing $id caused NullPointerException.
     */
    @Test
    public void testDereferenceWithoutId() {
        TypedContent content = TypedContent.create(
                resourceToContentHandle("schema-without-id.json"),
                ContentTypes.APPLICATION_JSON);
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();

        Map<String, TypedContent> resolvedReferences = new LinkedHashMap<>();
        resolvedReferences.put("simple-address.json", TypedContent.create(
                resourceToContentHandle("simple-address.json"), ContentTypes.APPLICATION_JSON));

        // Should not throw NullPointerException
        TypedContent modifiedContent = dereferencer.dereference(content, resolvedReferences);

        Assertions.assertNotNull(modifiedContent);
        Assertions.assertNotNull(modifiedContent.getContent());
        String dereferencedContent = modifiedContent.getContent().content();
        Assertions.assertTrue(dereferencedContent.contains("\"type\" : \"object\""));
        // Verify the reference was dereferenced - should contain address properties
        Assertions.assertTrue(dereferencedContent.contains("\"street\""));
        Assertions.assertTrue(dereferencedContent.contains("\"city\""));
    }

    /**
     * Test that dereferencing works when the schema is missing the $schema property.
     * This verifies the fix for issue #6944 where missing $schema caused NullPointerException.
     */
    @Test
    public void testDereferenceWithoutSchema() {
        TypedContent content = TypedContent.create(
                resourceToContentHandle("schema-without-schema.json"),
                ContentTypes.APPLICATION_JSON);
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();

        Map<String, TypedContent> resolvedReferences = new LinkedHashMap<>();
        resolvedReferences.put("simple-address.json", TypedContent.create(
                resourceToContentHandle("simple-address.json"), ContentTypes.APPLICATION_JSON));

        // Should not throw NullPointerException
        TypedContent modifiedContent = dereferencer.dereference(content, resolvedReferences);

        Assertions.assertNotNull(modifiedContent);
        Assertions.assertNotNull(modifiedContent.getContent());
        String dereferencedContent = modifiedContent.getContent().content();
        Assertions.assertTrue(dereferencedContent.contains("\"type\" : \"object\""));
        // Verify the reference was dereferenced - should contain address properties
        Assertions.assertTrue(dereferencedContent.contains("\"street\""));
        Assertions.assertTrue(dereferencedContent.contains("\"zipCode\""));
        // Verify the $id was preserved from the original schema
        Assertions.assertTrue(dereferencedContent.contains("\"$id\" : \"https://example.com/my-schema\""));
    }

    /**
     * Test that dereferencing works when the schema is missing both $id and $schema properties.
     * This verifies the fix for issue #6944 where missing properties caused NullPointerException.
     */
    @Test
    public void testDereferenceWithoutIdOrSchema() {
        TypedContent content = TypedContent.create(
                resourceToContentHandle("schema-without-id-or-schema.json"),
                ContentTypes.APPLICATION_JSON);
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();

        Map<String, TypedContent> resolvedReferences = new LinkedHashMap<>();
        resolvedReferences.put("simple-address.json", TypedContent.create(
                resourceToContentHandle("simple-address.json"), ContentTypes.APPLICATION_JSON));

        // Should not throw NullPointerException
        TypedContent modifiedContent = dereferencer.dereference(content, resolvedReferences);

        Assertions.assertNotNull(modifiedContent);
        Assertions.assertNotNull(modifiedContent.getContent());
        String dereferencedContent = modifiedContent.getContent().content();
        Assertions.assertTrue(dereferencedContent.contains("\"type\" : \"object\""));
        // Verify the reference was dereferenced - should contain address properties
        Assertions.assertTrue(dereferencedContent.contains("\"street\""));
        Assertions.assertTrue(dereferencedContent.contains("\"city\""));
        Assertions.assertTrue(dereferencedContent.contains("\"zipCode\""));
    }
    private static final String ADDRESS_SCHEMA = """
            {"definitions": {"Address": {"type": "object", "properties": {"street": {"type": "string"}}}}}
            """;

    private static final String CUSTOMER_WITH_ID = """
            {"$id": "https://example.com/schemas/customer/", "type": "object",
             "properties": {"address": {"$ref": "address.json#/definitions/Address"}}}
            """;

    private static TypedContent json(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON);
    }

    private static JsonNode dereference(String schema, Map<String, TypedContent> references) throws Exception {
        return new ObjectMapper()
                .readTree(new JsonSchemaDereferencer().dereference(json(schema), references).getContent().content());
    }

    /**
     * A reference registered under the literal $ref value dereferences in a schema with an $id.
     */
    @Test
    public void testDereferenceWithIdAndLiteralReferenceName() throws Exception {
        JsonNode result = dereference(CUSTOMER_WITH_ID,
                Map.of("address.json#/definitions/Address", json(ADDRESS_SCHEMA)));
        Assertions.assertEquals("string",
                result.at("/properties/address/properties/street/type").asText());
        Assertions.assertEquals("https://example.com/schemas/customer/", result.get("$id").asText());
    }

    /**
     * A reference registered under the $ref resolved against the schema's $id dereferences as well.
     */
    @Test
    public void testDereferenceWithIdAndResolvedReferenceName() throws Exception {
        JsonNode result = dereference(CUSTOMER_WITH_ID, Map.of(
                "https://example.com/schemas/customer/address.json#/definitions/Address", json(ADDRESS_SCHEMA)));
        Assertions.assertEquals("string",
                result.at("/properties/address/properties/street/type").asText());
    }

    /**
     * Identical relative $refs in different $id scopes resolve to the reference registered for each scope.
     */
    @Test
    public void testDereferenceSameRelativeRefInDifferentIdScopes() throws Exception {
        String schema = """
                {"type": "object", "properties": {
                  "billing": {"$id": "https://example.com/billing/", "type": "object",
                              "properties": {"a": {"$ref": "address.json"}}},
                  "shipping": {"$id": "https://example.com/shipping/", "type": "object",
                               "properties": {"a": {"$ref": "address.json"}}}
                }}
                """;
        JsonNode result = dereference(schema, Map.of(
                "https://example.com/billing/address.json",
                json("{\"type\": \"object\", \"properties\": {\"iban\": {\"type\": \"string\"}}}"),
                "https://example.com/shipping/address.json",
                json("{\"type\": \"object\", \"properties\": {\"street\": {\"type\": \"string\"}}}")));
        Assertions.assertEquals("string", result.at("/properties/billing/properties/a/properties/iban/type").asText());
        Assertions.assertEquals("string",
                result.at("/properties/shipping/properties/a/properties/street/type").asText());
        Assertions.assertTrue(result.at("/properties/billing/properties/a/properties/street").isMissingNode());
    }

    /**
     * REWRITE matches a reference registered under the $id-resolved URI, and falls back to the literal value.
     */
    @Test
    public void testRewriteReferencesWithId() throws Exception {
        String schema = """
                {"$id": "https://example.com/schemas/customer/", "type": "object", "properties": {
                  "address": {"$ref": "address.json#/definitions/Address"},
                  "ssn": {"$ref": "ssn.json"}
                }}
                """;
        TypedContent rewritten = new JsonSchemaDereferencer().rewriteReferences(json(schema), Map.of(
                "https://example.com/schemas/customer/address.json", "https://registry/address",
                "ssn.json", "https://registry/ssn"));
        JsonNode result = new ObjectMapper().readTree(rewritten.getContent().content());
        Assertions.assertEquals("https://registry/address#/definitions/Address",
                result.at("/properties/address/$ref").asText());
        Assertions.assertEquals("https://registry/ssn", result.at("/properties/ssn/$ref").asText());
    }

}
