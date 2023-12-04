package io.apicurio.registry.content.dereference;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.OpenApiReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;


public class OpenApiContentDereferencerTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testRewriteReferences() {
        ContentHandle content = resourceToContentHandle("openapi-to-rewrite.json");
        OpenApiDereferencer dereferencer = new OpenApiDereferencer();
        ContentHandle modifiedContent = dereferencer.rewriteReferences(content, Map.of(
                "./types/bar-types.json#/components/schemas/Bar", "https://www.example.org/schemas/bar-types.json#/components/schemas/Bar",
                "./types/foo-types.json#/components/schemas/Foo", "https://www.example.org/schemas/foo-types.json#/components/schemas/Foo"));
        
        ReferenceFinder finder = new OpenApiReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference("https://www.example.org/schemas/bar-types.json#/components/schemas/Bar")));
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference("https://www.example.org/schemas/foo-types.json#/components/schemas/Foo")));
    }

}
