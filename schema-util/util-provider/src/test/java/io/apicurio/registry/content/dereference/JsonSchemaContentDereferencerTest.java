package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.JsonSchemaReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

}
