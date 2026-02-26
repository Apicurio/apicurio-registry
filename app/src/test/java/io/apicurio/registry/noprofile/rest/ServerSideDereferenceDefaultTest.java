package io.apicurio.registry.noprofile.rest;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Tests for server-side dereference configuration feature when configuration is NOT set.
 * This test validates that when the global configuration property
 * {@code apicurio.rest.artifact.references.default-handling} is not configured,
 * the system maintains existing default behavior (PRESERVE for v3, false for v2).
 */
@QuarkusTest
public class ServerSideDereferenceDefaultTest extends AbstractResourceTestBase {

    private static final String GROUP = "ServerSideDereferenceDefaultTest";

    /**
     * Test that when server-side configuration is NOT set, v3 API defaults to PRESERVE.
     * This validates backward compatibility - existing behavior should be unchanged.
     */
    @Test
    public void testV3DefaultBehavior_NoServerConfig() throws Exception {
        String referencedTypesContent = resourceToString("openapi-referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testV3Default/ReferencedTypes", ArtifactType.OPENAPI,
                referencedTypesContent, ContentTypes.APPLICATION_JSON);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(GROUP)
                .artifactId("testV3Default/ReferencedTypes").version("1").build());
        createArtifactWithReferences(GROUP, "testV3Default/WithExternalRef", ArtifactType.OPENAPI,
                withExternalRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get content WITHOUT specifying references parameter
        // With no server config, should default to PRESERVE (existing behavior)
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testV3Default/WithExternalRef")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("openapi", equalTo("3.0.2"))
                // The reference should be PRESERVED (not inlined) - existing default behavior
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("./referenced-types.json#/components/schemas/Widget"));
    }

    /**
     * Test that when server-side configuration is NOT set, v2 API defaults to false for dereference.
     * This validates backward compatibility - existing behavior should be unchanged.
     */
    @Test
    public void testV2DefaultBehavior_NoServerConfig() throws Exception {
        String referencedTypesContent = resourceToString("openapi-referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testV2Default/ReferencedTypes", ArtifactType.OPENAPI,
                referencedTypesContent, ContentTypes.APPLICATION_JSON);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(GROUP)
                .artifactId("testV2Default/ReferencedTypes").version("1").build());
        createArtifactWithReferences(GROUP, "testV2Default/WithExternalRef", ArtifactType.OPENAPI,
                withExternalRefContent, ContentTypes.APPLICATION_JSON, refs);

        // Get latest artifact content WITHOUT specifying dereference parameter
        // With no server config, should default to false (existing behavior)
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testV2Default/WithExternalRef")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}").then().statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                // The reference should be PRESERVED (not inlined) - existing default behavior
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("./referenced-types.json#/components/schemas/Widget"));

        // Get specific version content WITHOUT specifying dereference parameter
        // With no server config, should default to false (existing behavior)
        given().when().pathParam("groupId", GROUP)
                .pathParam("artifactId", "testV2Default/WithExternalRef").pathParam("version", "1")
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(200).body("openapi", equalTo("3.0.2"))
                // The reference should be PRESERVED (not inlined) - existing default behavior
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("./referenced-types.json#/components/schemas/Widget"));
    }

    /**
     * Test that globalId endpoints also maintain default behavior when no config is set.
     */
    @Test
    public void testGlobalIdDefaultBehavior_NoServerConfig() throws Exception {
        String referencedTypesContent = resourceToString("openapi-referenced-types.json");
        String withExternalRefContent = resourceToString("openapi-with-external-ref.json");

        // Create the artifact containing a type to be referenced
        createArtifact(GROUP, "testGlobalIdDefault/ReferencedTypes", ArtifactType.OPENAPI,
                referencedTypesContent, ContentTypes.APPLICATION_JSON);

        // Create the artifact that references the type
        List<ArtifactReference> refs = Collections.singletonList(ArtifactReference.builder()
                .name("./referenced-types.json#/components/schemas/Widget").groupId(GROUP)
                .artifactId("testGlobalIdDefault/ReferencedTypes").version("1").build());
        var metadata = createArtifactWithReferences(GROUP, "testGlobalIdDefault/WithExternalRef",
                ArtifactType.OPENAPI, withExternalRefContent, ContentTypes.APPLICATION_JSON, refs);

        // v3 API - Get content by globalId WITHOUT specifying references parameter
        // Should default to PRESERVE (existing behavior)
        given().when().pathParam("globalId", metadata.getVersion().getGlobalId())
                .get("/registry/v3/ids/globalIds/{globalId}").then().statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                // The reference should be PRESERVED (not inlined)
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("./referenced-types.json#/components/schemas/Widget"));

        // v2 API - Get content by globalId WITHOUT specifying dereference parameter
        // Should default to false (existing behavior)
        given().when().pathParam("globalId", metadata.getVersion().getGlobalId())
                .get("/registry/v2/ids/globalIds/{globalId}").then().statusCode(200)
                .body("openapi", equalTo("3.0.2"))
                // The reference should be PRESERVED (not inlined)
                .body("paths.widgets.get.responses.200.content.json.schema.items.$ref",
                        equalTo("./referenced-types.json#/components/schemas/Widget"));
    }
}
