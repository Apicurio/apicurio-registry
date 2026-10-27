package io.apicurio.registry.content.refs;

import io.apicurio.registry.asyncapi.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.content.refs.JsonSchemaReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class JsonSchemaReferenceFinderTest extends ArtifactUtilProviderTestBase {

    /**
     * Test method for
     * {@link AsyncApiReferenceFinder#findExternalReferences(io.apicurio.registry.content.ContentHandle)}.
     */
    @Test
    public void testFindExternalReferences() {
        TypedContent content = resourceToTypedContentHandle("json-schema-with-refs.json");
        JsonSchemaReferenceFinder finder = new JsonSchemaReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertEquals(2, foundReferences.size());
        Assertions.assertEquals(Set.of(new JsonPointerExternalReference("./address.json"),
                new JsonPointerExternalReference("./ssn.json")), foundReferences);
    }

    /**
     * Reference names stay the literal $ref values when the schema declares a base URI, since that is what
     * the dereferencer and rewriter match on.
     */
    @Test
    public void testFindExternalReferencesKeepsLiteralNamesWithId() {
        TypedContent content = TypedContent.create(ContentHandle.create("""
                {"$id": "https://example.com/schemas/customer/",
                 "properties": {"address": {"$ref": "address.json#/definitions/Address"}}}
                """), ContentTypes.APPLICATION_JSON);
        Set<ExternalReference> foundReferences = new JsonSchemaReferenceFinder().findExternalReferences(content);
        Assertions.assertEquals(Set.of(new JsonPointerExternalReference("address.json#/definitions/Address")),
                foundReferences);
    }

    /**
     * A $ref that is not a valid URI reference is still reported and does not fail detection.
     */
    @Test
    public void testFindExternalReferencesWithInvalidUri() {
        TypedContent content = TypedContent.create(ContentHandle.create("""
                {"$id": "https://example.com/schemas/",
                 "properties": {"a": {"$ref": "my file.json"}, "b": {"$ref": "address.json"}}}
                """), ContentTypes.APPLICATION_JSON);
        Set<ExternalReference> foundReferences = new JsonSchemaReferenceFinder().findExternalReferences(content);
        Assertions.assertEquals(Set.of(new JsonPointerExternalReference("my file.json"),
                new JsonPointerExternalReference("address.json")), foundReferences);
    }

}
