package io.apicurio.registry.content.dereference;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;


public class AsyncApiContentDereferencerTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testRewriteReferences() {
        ContentHandle content = resourceToContentHandle("asyncapi-to-rewrite.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        ContentHandle modifiedContent = dereferencer.rewriteReferences(content, Map.of(
                "./TradeKey.avsc", "https://www.example.org/schemas/TradeKey.avsc",
                "./common-types.json#/components/schemas/User", "https://www.example.org/schemas/common-types.json#/components/schemas/User"));
        
        ReferenceFinder finder = new AsyncApiReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference("https://www.example.org/schemas/common-types.json#/components/schemas/User")));
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference("https://www.example.org/schemas/TradeKey.avsc")));
    }

}
