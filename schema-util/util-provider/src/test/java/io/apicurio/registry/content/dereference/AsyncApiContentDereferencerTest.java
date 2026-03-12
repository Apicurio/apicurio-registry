package io.apicurio.registry.content.dereference;

import io.apicurio.registry.asyncapi.content.dereference.AsyncApiDereferencer;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.asyncapi.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static io.apicurio.registry.utils.tests.TestUtils.normalizeMultiLineString;

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

    /**
     * Test dereferencing an AsyncAPI 3.0 document with a reference to an external AsyncAPI 3.0 schema.
     */
    @Test
    public void testDereferenceAsyncApi30ToAsyncApi30() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-asyncapi.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map.of(
                "http://types.example.org/asyncapi30-common-types.json#/components/schemas/User",
                TypedContent.create(resourceToContentHandle("asyncapi30-common-types.json"),
                        ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-asyncapi30-asyncapi.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document with a reference to an Avro schema.
     * The Avro schema should be imported as an object (not wrapped in MultiFormatSchema).
     */
    @Test
    public void testDereferenceAsyncApi30ToAvro() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-avro.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map.of("http://schemas.example.org/user-event.avsc",
                TypedContent.create(resourceToContentHandle("user-event.avsc"), ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-asyncapi30-avro.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document with a reference to a Protobuf schema.
     * The Protobuf schema should be imported with x-text-content vendor extension.
     */
    @Test
    public void testDereferenceAsyncApi30ToProtobuf() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-protobuf.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map
                .of("http://schemas.example.org/user-profile.proto", TypedContent
                        .create(resourceToContentHandle("user-profile.proto"), "application/x-protobuf"));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-asyncapi30-protobuf.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

    /**
     * Test dereferencing an AsyncAPI 3.0 document with references to JSON Schema definitions.
     * The JSON Schema definitions should be properly inlined.
     */
    @Test
    public void testDereferenceAsyncApi30ToJsonSchema() throws Exception {
        ContentHandle content = resourceToContentHandle("asyncapi30-to-deref-jsonschema.json");
        AsyncApiDereferencer dereferencer = new AsyncApiDereferencer();
        Map<String, TypedContent> resolvedReferences = Map.of(
                "http://schemas.example.org/common-schemas.json#/definitions/Address",
                TypedContent.create(resourceToContentHandle("common-schemas.json"),
                        ContentTypes.APPLICATION_JSON),
                "http://schemas.example.org/common-schemas.json#/definitions/Contact",
                TypedContent.create(resourceToContentHandle("common-schemas.json"),
                        ContentTypes.APPLICATION_JSON));
        TypedContent modifiedContent = dereferencer
                .dereference(TypedContent.create(content, ContentTypes.APPLICATION_JSON), resolvedReferences);
        String expectedContent = resourceToString("expected-testDereference-asyncapi30-jsonschema.json");
        Assertions.assertEquals(normalizeMultiLineString(expectedContent),
                normalizeMultiLineString(modifiedContent.getContent().content()));
    }

}
