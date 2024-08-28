package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.JsonSchemaReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
    public void testDereferenceObjectLevel() {
        ContentHandle content = resourceToContentHandle("json-schema-to-deref-object-level.json");
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();
        // Note: order is important.  The JSON schema dereferencer needs to convert the ContentHandle Map
        // to a JSONSchema map.  So it *must* resolve the leaves of the dependency tree before the branches.
        Map<String, ContentHandle> resolvedReferences = new LinkedHashMap<>();
        resolvedReferences.put("types/city/qualification.json", resourceToContentHandle("types/city/qualification.json"));
        resolvedReferences.put("city/qualification.json", resourceToContentHandle("types/city/qualification.json"));
        resolvedReferences.put("identifier/qualification.json", resourceToContentHandle("types/identifier/qualification.json"));
        resolvedReferences.put("types/all-types.json#/definitions/City", resourceToContentHandle("types/all-types.json"));
        resolvedReferences.put("types/all-types.json#/definitions/Identifier", resourceToContentHandle("types/all-types.json"));
        ContentHandle modifiedContent = dereferencer.dereference(content, resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-object-level-json.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent), normalizeMultiLineString(modifiedContent.content()));
    }

    @Test
    public void testDereferencePropertyLevel() {
        ContentHandle content = resourceToContentHandle("json-schema-to-deref-property-level.json");
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();
        // Note: order is important.  The JSON schema dereferencer needs to convert the ContentHandle Map
        // to a JSONSchema map.  So it *must* resolve the leaves of the dependency tree before the branches.
        Map<String, ContentHandle> resolvedReferences = new LinkedHashMap<>();
        resolvedReferences.put("types/city/qualification.json", resourceToContentHandle("types/city/qualification.json"));
        resolvedReferences.put("city/qualification.json", resourceToContentHandle("types/city/qualification.json"));
        resolvedReferences.put("identifier/qualification.json", resourceToContentHandle("types/identifier/qualification.json"));
        resolvedReferences.put("types/all-types.json#/definitions/City/properties/name", resourceToContentHandle("types/all-types.json"));
        resolvedReferences.put("types/all-types.json#/definitions/Identifier", resourceToContentHandle("types/all-types.json"));
        ContentHandle modifiedContent = dereferencer.dereference(content, resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-property-level-json.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent), normalizeMultiLineString(modifiedContent.content()));
    }
}
