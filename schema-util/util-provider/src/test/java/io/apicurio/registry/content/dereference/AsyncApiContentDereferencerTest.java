package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.types.ContentTypes;
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

}
