package io.apicurio.registry.content.dereference;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.JsonSchemaReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;

public class JsonSchemaContentDereferencerTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testRewriteReferences() {
        ContentHandle content = resourceToContentHandle("json-schema-to-rewrite.json");
        JsonSchemaDereferencer dereferencer = new JsonSchemaDereferencer();
        ContentHandle modifiedContent = dereferencer.rewriteReferences(content, Map.of(
                "./address.json", "https://www.example.org/schemas/address.json",
                "./ssn.json", "https://www.example.org/schemas/ssn.json"));
        
        ReferenceFinder finder = new JsonSchemaReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference("https://www.example.org/schemas/address.json")));
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference("https://www.example.org/schemas/ssn.json")));
    }

}
