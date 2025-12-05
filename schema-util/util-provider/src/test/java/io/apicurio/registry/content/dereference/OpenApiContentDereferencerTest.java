package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.openapi.content.dereference.OpenApiDereferencer;
import io.apicurio.registry.openapi.content.refs.OpenApiReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static io.apicurio.registry.utils.tests.TestUtils.normalizeMultiLineString;

public class OpenApiContentDereferencerTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testRewriteReferences() {
        TypedContent content = resourceToTypedContentHandle("openapi-to-rewrite.json");
        OpenApiDereferencer dereferencer = new OpenApiDereferencer();
        TypedContent modifiedContent = dereferencer.rewriteReferences(content,
                Map.of("./types/bar-types.json#/components/schemas/Bar",
                        "https://www.example.org/schemas/bar-types.json#/components/schemas/Bar",
                        "./types/foo-types.json#/components/schemas/Foo",
                        "https://www.example.org/schemas/foo-types.json#/components/schemas/Foo"));

        ReferenceFinder finder = new OpenApiReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference(
                "https://www.example.org/schemas/bar-types.json#/components/schemas/Bar")));
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference(
                "https://www.example.org/schemas/foo-types.json#/components/schemas/Foo")));
    }

    @Test
    public void testDereference() throws Exception {
        ContentHandle content = resourceToContentHandle("openapi-to-deref.json");
        OpenApiDereferencer dereferencer = new OpenApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map.of(
                "http://types.example.org/all-types.json#/components/schemas/Foo",
                TypedContent.create(resourceToContentHandle("all-types.json"), ContentTypes.APPLICATION_JSON),
                "http://types.example.org/all-types.json#/components/schemas/Bar",
                TypedContent.create(resourceToContentHandle("all-types.json"), ContentTypes.APPLICATION_JSON),
                "http://types.example.org/address.json#/components/schemas/Address",
                TypedContent.create(resourceToContentHandle("address.json"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-openapi.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    @Test
    public void testRewriteReferencesPreservesYamlFormat() {
        TypedContent content = resourceToTypedContentHandle("openapi-to-rewrite.yaml");
        OpenApiDereferencer dereferencer = new OpenApiDereferencer();
        TypedContent modifiedContent = dereferencer.rewriteReferences(content,
                Map.of("./types/bar-types.json#/components/schemas/Bar",
                        "https://www.example.org/schemas/bar-types.json#/components/schemas/Bar",
                        "./types/foo-types.json#/components/schemas/Foo",
                        "https://www.example.org/schemas/foo-types.json#/components/schemas/Foo"));

        // Verify that the content type is still YAML
        Assertions.assertEquals(ContentTypes.APPLICATION_YAML, modifiedContent.getContentType());

        // Verify that the content is valid YAML (not JSON)
        String contentString = modifiedContent.getContent().content();
        Assertions.assertFalse(contentString.trim().startsWith("{"),
                "Content should be YAML, not JSON");

        // Verify that references were rewritten by checking the content
        ReferenceFinder finder = new OpenApiReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference(
                "https://www.example.org/schemas/bar-types.json#/components/schemas/Bar")));
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference(
                "https://www.example.org/schemas/foo-types.json#/components/schemas/Foo")));
    }

    @Test
    public void testRewriteReferencesPreservesJsonFormat() {
        TypedContent content = resourceToTypedContentHandle("openapi-to-rewrite.json");
        OpenApiDereferencer dereferencer = new OpenApiDereferencer();
        TypedContent modifiedContent = dereferencer.rewriteReferences(content,
                Map.of("./types/bar-types.json#/components/schemas/Bar",
                        "https://www.example.org/schemas/bar-types.json#/components/schemas/Bar",
                        "./types/foo-types.json#/components/schemas/Foo",
                        "https://www.example.org/schemas/foo-types.json#/components/schemas/Foo"));

        // Verify that the content type is still JSON
        Assertions.assertEquals(ContentTypes.APPLICATION_JSON, modifiedContent.getContentType());

        // Verify that the content is valid JSON (starts with {)
        String contentString = modifiedContent.getContent().content();
        Assertions.assertTrue(contentString.trim().startsWith("{"),
                "Content should be JSON");

        // Verify that references were rewritten
        ReferenceFinder finder = new OpenApiReferenceFinder();
        Set<ExternalReference> externalReferences = finder.findExternalReferences(modifiedContent);
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference(
                "https://www.example.org/schemas/bar-types.json#/components/schemas/Bar")));
        Assertions.assertTrue(externalReferences.contains(new JsonPointerExternalReference(
                "https://www.example.org/schemas/foo-types.json#/components/schemas/Foo")));
    }

}
