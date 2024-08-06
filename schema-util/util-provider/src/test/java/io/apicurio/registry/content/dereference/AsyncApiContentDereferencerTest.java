package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

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

}
