package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.JsonSchemaReferenceFinder;
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
}
