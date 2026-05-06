package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
public class ContentResourceTest extends AbstractResourceTestBase {

    /**
     * Tests detecting references in an OpenAPI document that uses external $ref values.
     */
    @Test
    public void testDetectOpenApiReferences() {
        String content = resourceToString("openapi-with-external-ref.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.OPENAPI;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(1, refs.size());
        Assertions.assertEquals("./referenced-types.json#/components/schemas/Widget", refs.get(0).getName());
    }

    /**
     * Tests detecting references in a JSON Schema that uses $ref values pointing to external files.
     */
    @Test
    public void testDetectJsonSchemaReferences() {
        String content = resourceToString("jsonschema-with-ref.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.JSON;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(2, refs.size());

        List<String> names = refs.stream().map(ArtifactReference::getName).toList();
        Assertions.assertTrue(names.contains("customer.json#/$defs/Customer"));
        Assertions.assertTrue(names.contains("address.json"));
    }

    /**
     * Tests detecting references in a Protobuf schema that uses import statements.
     */
    @Test
    public void testDetectProtobufReferences() {
        String content = resourceToString("protobuf-with-imports.proto");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_PROTOBUF);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.PROTOBUF;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(2, refs.size());

        List<String> names = refs.stream().map(ArtifactReference::getName).toList();
        Assertions.assertTrue(names.contains("common/address.proto"));
        Assertions.assertTrue(names.contains("common/phone_number.proto"));
    }

    /**
     * Tests detecting references in an Avro schema that uses external type references.
     */
    @Test
    public void testDetectAvroReferences() {
        String content = resourceToString(
                "/io/apicurio/registry/noprofile/rest/avro-with-reference.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.AVRO;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(1, refs.size());
        Assertions.assertEquals("com.example.common.Address", refs.get(0).getName());
    }

    /**
     * Tests that content with no external references returns an empty list.
     */
    @Test
    public void testDetectReferencesNone() {
        String content = resourceToString("jsonschema-valid.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.JSON;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertTrue(refs.isEmpty());
    }

    /**
     * Tests that artifact type is auto-detected from the content when not provided.
     */
    @Test
    public void testDetectReferencesAutoDetectType() {
        String content = resourceToString("openapi-with-external-ref.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        // No artifactType — should auto-detect as OpenAPI
        List<ArtifactReference> refs = clientV3.content().references().post(body);

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(1, refs.size());
        Assertions.assertEquals("./referenced-types.json#/components/schemas/Widget", refs.get(0).getName());
    }

    /**
     * Tests that only the name field is populated in detected references
     * (groupId, artifactId, and version should be null).
     */
    @Test
    public void testDetectReferencesOnlyNamePopulated() {
        String content = resourceToString("jsonschema-with-ref.json");

        VersionContent body = new VersionContent();
        body.setContent(content);
        body.setContentType(ContentTypes.APPLICATION_JSON);

        List<ArtifactReference> refs = clientV3.content().references().post(body, config -> {
            config.queryParameters.artifactType = ArtifactType.JSON;
        });

        Assertions.assertNotNull(refs);
        Assertions.assertEquals(2, refs.size());
        for (ArtifactReference ref : refs) {
            Assertions.assertNotNull(ref.getName());
            Assertions.assertNull(ref.getGroupId());
            Assertions.assertNull(ref.getArtifactId());
            Assertions.assertNull(ref.getVersion());
        }
    }
}
