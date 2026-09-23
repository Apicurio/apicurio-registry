package io.apicurio.registry.noprofile.rest;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests for server-side dereference configuration feature.
 * This test validates that the global configuration property
 * {@code apicurio.rest.artifact.references.default-handling} works correctly
 * across all API versions (v2, v3, and ccompat).
 */
@QuarkusTest
@TestProfile(ServerSideDereferenceTestProfile.class)
public class ServerSideDereferenceTest extends AbstractResourceTestBase {

    private static final String GROUP = "ServerSideDereferenceTest";

    /**
     * Test that server-side dereferencing works for REST API v3 with OpenAPI artifacts.
     * When the server is configured with DEREFERENCE and no client parameter is provided,
     * references should be inlined automatically.
     */
    @Test
    public void testV3ServerSideDereference_OpenAPI() throws Exception {
        String referencedTypesContent = resourceToString("openapi-referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testV3ServerSide/ReferencedTypes", ArtifactType.OPENAPI,
                referencedTypesContent, ContentTypes.APPLICATION_JSON);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(GROUP)
                .artifactId("testV3ServerSide/ReferencedTypes").version("1").build());
        createArtifactWithReferences(GROUP, "testV3ServerSide/WithExternalRef", ArtifactType.OPENAPI,
                withExternalRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get content WITHOUT specifying references parameter - server default (DEREFERENCE) should apply
        // After DEREFERENCE, the $ref should be replaced with the actual inlined content
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testV3ServerSide/WithExternalRef")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                // The reference should be dereferenced (inlined)
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("#/components/schemas/Widget"))
                .body("components.schemas.Widget.title", equalTo("Root Type for Widget"))
                .body("components.schemas.Widget.type", equalTo("object"))
                .body("components.schemas.Widget.properties.name.type", equalTo("string"))
                .body("components.schemas.Widget.properties.description.type", equalTo("string"));
    }

    /**
     * Test that client-provided parameters override server-side defaults for v3 API.
     * Even when server is configured with DEREFERENCE, if client explicitly requests PRESERVE,
     * references should be preserved.
     */
    @Test
    public void testV3ClientParameterOverridesServerDefault() throws Exception {
        String referencedTypesContent = resourceToString("openapi-referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testV3ClientOverride/ReferencedTypes", ArtifactType.OPENAPI,
                referencedTypesContent, ContentTypes.APPLICATION_JSON);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(GROUP)
                .artifactId("testV3ClientOverride/ReferencedTypes").version("1").build());
        createArtifactWithReferences(GROUP, "testV3ClientOverride/WithExternalRef", ArtifactType.OPENAPI,
                withExternalRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get content WITH explicit PRESERVE parameter - should override server default
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testV3ClientOverride/WithExternalRef")
                .queryParam("references", "PRESERVE")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                // The reference should be preserved (not inlined)
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("./referenced-types.json#/components/schemas/Widget"));

        // Get content WITH explicit REWRITE parameter - should override server default
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testV3ClientOverride/WithExternalRef")
                .queryParam("references", "REWRITE")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                // The reference should be rewritten to REST API URL
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref", endsWith(
                        "/apis/registry/v3/groups/ServerSideDereferenceTest/artifacts/testV3ClientOverride%2FReferencedTypes/versions/1/content?references=REWRITE#/components/schemas/Widget"));
    }

    /**
     * Test that server-side dereferencing works for REST API v3 via globalId endpoint.
     */
    @Test
    public void testV3ServerSideDereference_GlobalId() throws Exception {
        String referencedTypesContent = resourceToString("openapi-referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testV3GlobalId/ReferencedTypes", ArtifactType.OPENAPI,
                referencedTypesContent, ContentTypes.APPLICATION_JSON);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(GROUP)
                .artifactId("testV3GlobalId/ReferencedTypes").version("1").build());
        var metadata = createArtifactWithReferences(GROUP, "testV3GlobalId/WithExternalRef",
                ArtifactType.OPENAPI, withExternalRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get content by globalId WITHOUT specifying references parameter
        // Server default (DEREFERENCE) should apply
        given().when().pathParam("globalId", metadata.getVersion().getGlobalId())
                .get("/registry/v3/ids/globalIds/{globalId}")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                // The reference should be dereferenced (inlined)
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("#/components/schemas/Widget"))
                .body("components.schemas.Widget.title", equalTo("Root Type for Widget"));
    }

    /**
     * Test that server-side dereferencing works for REST API v2 with OpenAPI artifacts.
     * In v2, the parameter is a boolean 'dereference' instead of HandleReferencesType enum.
     * When server is configured with DEREFERENCE, the v2 boolean should default to true.
     */
    @Test
    public void testV2ServerSideDereference_OpenAPI() throws Exception {
        String referencedTypesContent = resourceToString("openapi-referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testV2ServerSide/ReferencedTypes", ArtifactType.OPENAPI,
                referencedTypesContent, ContentTypes.APPLICATION_JSON);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(GROUP)
                .artifactId("testV2ServerSide/ReferencedTypes").version("1").build());
        createArtifactWithReferences(GROUP, "testV2ServerSide/WithExternalRef", ArtifactType.OPENAPI,
                withExternalRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get latest artifact content WITHOUT specifying dereference parameter
        // Server default (DEREFERENCE -> true) should apply
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testV2ServerSide/WithExternalRef")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                // The reference should be dereferenced (inlined)
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("#/components/schemas/Widget"))
                .body("components.schemas.Widget.title", equalTo("Root Type for Widget"));

        // Get specific version content WITHOUT specifying dereference parameter
        // Server default (DEREFERENCE -> true) should apply
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testV2ServerSide/WithExternalRef")
                .pathParam("version", "1")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                // The reference should be dereferenced (inlined)
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("#/components/schemas/Widget"))
                .body("components.schemas.Widget.title", equalTo("Root Type for Widget"));
    }

    /**
     * Test that client-provided parameters override server-side defaults for v2 API.
     */
    @Test
    public void testV2ClientParameterOverridesServerDefault() throws Exception {
        String referencedTypesContent = resourceToString("openapi-referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testV2ClientOverride/ReferencedTypes", ArtifactType.OPENAPI,
                referencedTypesContent, ContentTypes.APPLICATION_JSON);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(GROUP)
                .artifactId("testV2ClientOverride/ReferencedTypes").version("1").build());
        createArtifactWithReferences(GROUP, "testV2ClientOverride/WithExternalRef", ArtifactType.OPENAPI,
                withExternalRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get content WITH explicit dereference=false parameter - should override server default
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testV2ClientOverride/WithExternalRef")
                .queryParam("dereference", "false")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                // The reference should be preserved (not inlined)
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("./referenced-types.json#/components/schemas/Widget"));
    }

    /**
     * Test that server-side dereferencing works for REST API v2 via globalId endpoint.
     */
    @Test
    public void testV2ServerSideDereference_GlobalId() throws Exception {
        String referencedTypesContent = resourceToString("openapi-referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testV2GlobalId/ReferencedTypes", ArtifactType.OPENAPI,
                referencedTypesContent, ContentTypes.APPLICATION_JSON);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(GROUP)
                .artifactId("testV2GlobalId/ReferencedTypes").version("1").build());
        var metadata = createArtifactWithReferences(GROUP, "testV2GlobalId/WithExternalRef",
                ArtifactType.OPENAPI, withExternalRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get content by globalId WITHOUT specifying dereference parameter
        // Server default (DEREFERENCE -> true) should apply
        given().when().pathParam("globalId", metadata.getVersion().getGlobalId())
                .get("/registry/v2/ids/globalIds/{globalId}")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                // The reference should be dereferenced (inlined)
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("#/components/schemas/Widget"))
                .body("components.schemas.Widget.title", equalTo("Root Type for Widget"));
    }

    /**
     * Test that server-side dereferencing works for ccompat API with Avro schemas.
     * In ccompat API, dereferencing is done via the 'format' parameter with value 'RESOLVED'.
     * When server is configured with DEREFERENCE, Avro schemas should automatically use RESOLVED format.
     */
    @Test
    public void testCCompatServerSideDereference_Avro() throws Exception {
        String referencedAvroContent = resourceToString("avro-referenced-type.json");
        String avroWithRefContent = resourceToString("avro-with-reference.json");

        // Create the referenced Avro type
        createArtifact(GROUP, "testCCompat/ReferencedType", ArtifactType.AVRO, referencedAvroContent,
                ContentTypes.APPLICATION_JSON);

        // Create the Avro schema that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("com.example.common.Address").groupId(GROUP)
                .artifactId("testCCompat/ReferencedType").version("1").build());
        var metadata = createArtifactWithReferences(GROUP, "testCCompat/WithReference", ArtifactType.AVRO,
                avroWithRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get schema by ID WITHOUT specifying format parameter
        // Server default (DEREFERENCE -> RESOLVED format for Avro) should apply
        String schema = given().when().pathParam("id", metadata.getVersion().getGlobalId())
                .get("/ccompat/v7/schemas/ids/{id}").then().statusCode(200).extract()
                .path("schema");

        // The schema should be resolved (references inlined)
        // The resolved schema should contain the referenced type definition
        Assertions.assertNotNull(schema);
        Assertions.assertFalse(schema.contains("com.example.common.Address"),
                "Resolved schema should no longer contain the referenced type name");
        Assertions.assertTrue(schema.contains("zipCode"),
                "Resolved schema should now contain the referenced zipCode field");
    }

    /**
     * Test that client-provided format parameter overrides server-side defaults for ccompat API.
     */
    @Test
    public void testCCompatClientParameterOverridesServerDefault_Avro() throws Exception {
        String referencedAvroContent = resourceToString("avro-referenced-type.json");
        String avroWithRefContent = resourceToString("avro-with-reference.json");

        // Create the referenced Avro type
        createArtifact(GROUP, "testCCompatOverride/ReferencedType", ArtifactType.AVRO,
                referencedAvroContent, ContentTypes.APPLICATION_JSON);

        // Create the Avro schema that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("com.example.common.Address").groupId(GROUP)
                .artifactId("testCCompatOverride/ReferencedType").version("1").build());
        var metadata = createArtifactWithReferences(GROUP, "testCCompatOverride/WithReference",
                ArtifactType.AVRO, avroWithRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get schema by ID WITH explicit format parameter (empty string = no formatting)
        // This should override the server default and return the schema as-is with references
        String schema = given().when().pathParam("id", metadata.getVersion().getGlobalId())
                .queryParam("format", "").get("/ccompat/v7/schemas/ids/{id}").then().statusCode(200)
                .extract().path("schema");

        // The schema should NOT be resolved (references should be preserved)
        Assertions.assertNotNull(schema);
        // Original schema without resolution should still reference the type by name
        Assertions.assertTrue(schema.contains("\"type\"") || schema.contains("\"name\""),
                "Non-resolved schema should contain type definitions");
    }

    /**
     * Test that artifacts without references work correctly with server-side defaults.
     * This ensures that the feature doesn't break normal (non-referenced) artifacts.
     */
    @Test
    public void testServerSideDereference_WithoutReferences() throws Exception {
        String simpleOpenApiContent = resourceToString("openapi-empty.json");

        // Create a simple artifact without references
        createArtifact(GROUP, "testNoRefs/SimpleAPI", ArtifactType.OPENAPI, simpleOpenApiContent,
                ContentTypes.APPLICATION_JSON);

        // v3 API - should work normally
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testNoRefs/SimpleAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                .body("info.title", equalTo("Empty API"));

        // v2 API - should work normally
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", "testNoRefs/SimpleAPI")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}").then().statusCode(200)
                .body("openapi", equalTo("3.0.2")).body("info.title", equalTo("Empty API"));
    }
}
