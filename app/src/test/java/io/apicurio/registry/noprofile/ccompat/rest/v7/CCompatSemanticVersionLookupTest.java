package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;

/**
 * Tests that the ccompat v7 API correctly resolves artifacts by integer sequence number
 * (versionOrder) when the artifact was registered with a semantic version string (e.g.
 * "v5.1.1"). The ccompat API uses integer sequence numbers per the Confluent spec, so
 * lookups using those integers must work regardless of how the artifact was originally
 * versioned.
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/7886">Issue #7886</a>
 */
@QuarkusTest
public class CCompatSemanticVersionLookupTest extends AbstractResourceTestBase {

    /**
     * Registers an artifact via the v3 API with a semantic version string, then verifies
     * that the ccompat subjects/versions endpoint can resolve it by its integer sequence
     * number.
     */
    @Test
    public void testGetSubjectVersionBySequenceNumber() throws Exception {
        final String groupId = "default";
        final String artifactId = TestUtils.generateArtifactId();

        final String schema = "{\"type\":\"record\",\"name\":\"TestRecord\","
                + "\"namespace\":\"com.example\",\"fields\":"
                + "[{\"name\":\"field1\",\"type\":\"string\"}]}";

        // Register via v3 API with a semantic version
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AVRO);
        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion("v5.1.1");
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(schema);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        createArtifact.setFirstVersion(createVersion);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // The ccompat API should list version 1 (the versionOrder) for this subject
        var versionsResponse = given()
                .when()
                .get("/ccompat/v7/subjects/{subject}/versions", artifactId)
                .then()
                .statusCode(200)
                .extract()
                .as(int[].class);
        Assertions.assertEquals(1, versionsResponse.length);
        Assertions.assertEquals(1, versionsResponse[0],
                "ccompat should return versionOrder (1), not the semantic version");

        // Now resolve that version via GET /subjects/{subject}/versions/1
        // This is the core of issue #7886: this call fails when the stored version is "v5.1.1"
        given()
                .when()
                .get("/ccompat/v7/subjects/{subject}/versions/{version}", artifactId, 1)
                .then()
                .statusCode(200);
    }

    /**
     * Registers an artifact with a semantic version that has a reference to another
     * semantic-versioned artifact, then verifies that reference resolution via the ccompat
     * API works using integer sequence numbers.
     */
    @Test
    public void testReferenceResolutionWithSemanticVersions() throws Exception {
        final String groupId = "default";
        final String referencedId = TestUtils.generateArtifactId();
        final String referencingId = TestUtils.generateArtifactId();

        // Create the referenced artifact with a semantic version
        final String referencedSchema = "{\"type\":\"record\",\"name\":\"Address\","
                + "\"namespace\":\"com.example\",\"fields\":"
                + "[{\"name\":\"street\",\"type\":\"string\"}]}";

        CreateArtifact createReferenced = new CreateArtifact();
        createReferenced.setArtifactId(referencedId);
        createReferenced.setArtifactType(ArtifactType.AVRO);
        CreateVersion refVersion = new CreateVersion();
        refVersion.setVersion("v2.0.0");
        VersionContent refContent = new VersionContent();
        refContent.setContent(referencedSchema);
        refContent.setContentType(ContentTypes.APPLICATION_JSON);
        refVersion.setContent(refContent);
        createReferenced.setFirstVersion(refVersion);
        clientV3.groups().byGroupId(groupId).artifacts().post(createReferenced);

        // Create the referencing artifact with a reference to the semantic-versioned artifact
        final String referencingSchema = "{\"type\":\"record\",\"name\":\"Person\","
                + "\"namespace\":\"com.example\",\"fields\":"
                + "[{\"name\":\"name\",\"type\":\"string\"},"
                + "{\"name\":\"address\",\"type\":\"com.example.Address\"}]}";

        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(groupId);
        ref.setArtifactId(referencedId);
        ref.setVersion("v2.0.0");
        ref.setName("com.example.Address");

        CreateArtifact createReferencing = new CreateArtifact();
        createReferencing.setArtifactId(referencingId);
        createReferencing.setArtifactType(ArtifactType.AVRO);
        CreateVersion referencingVersion = new CreateVersion();
        referencingVersion.setVersion("v1.0.0");
        VersionContent referencingContent = new VersionContent();
        referencingContent.setContent(referencingSchema);
        referencingContent.setContentType(ContentTypes.APPLICATION_JSON);
        referencingContent.setReferences(List.of(ref));
        referencingVersion.setContent(referencingContent);
        createReferencing.setFirstVersion(referencingVersion);
        var result = clientV3.groups().byGroupId(groupId).artifacts().post(createReferencing);

        long contentId = result.getVersion().getContentId();

        // Fetch the schema via ccompat — the references should have integer version numbers
        var ccompatSchema = given()
                .when()
                .get("/ccompat/v7/schemas/ids/{id}", contentId)
                .then()
                .statusCode(200)
                .extract()
                .as(io.apicurio.registry.ccompat.rest.v7.beans.Schema.class);

        Assertions.assertNotNull(ccompatSchema.getReferences());
        Assertions.assertEquals(1, ccompatSchema.getReferences().size());
        int refVersionNumber = ccompatSchema.getReferences().get(0).getVersion().intValue();

        // Now use that integer version number to resolve the referenced artifact via ccompat.
        // This is the second part of issue #7886: the integer returned in references must be
        // usable to fetch the referenced schema.
        given()
                .when()
                .get("/ccompat/v7/subjects/{subject}/versions/{version}", referencedId, refVersionNumber)
                .then()
                .statusCode(200);
    }

    /**
     * Verifies that listing versions via the ccompat API returns versionOrder integers
     * (not the semantic version strings) when the artifact was registered with semantic
     * versioning.
     */
    @Test
    public void testListVersionsReturnsSequenceNumbers() throws Exception {
        final String groupId = "default";
        final String artifactId = TestUtils.generateArtifactId();

        final String schemaV1 = "{\"type\":\"record\",\"name\":\"Rec\","
                + "\"namespace\":\"com.example\",\"fields\":"
                + "[{\"name\":\"f1\",\"type\":\"string\"}]}";
        final String schemaV2 = "{\"type\":\"record\",\"name\":\"Rec\","
                + "\"namespace\":\"com.example\",\"fields\":"
                + "[{\"name\":\"f1\",\"type\":\"string\"},{\"name\":\"f2\",\"type\":\"string\"}]}";

        // Register first version with semantic version "v1.0.0"
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AVRO);
        CreateVersion v1 = new CreateVersion();
        v1.setVersion("v1.0.0");
        VersionContent content1 = new VersionContent();
        content1.setContent(schemaV1);
        content1.setContentType(ContentTypes.APPLICATION_JSON);
        v1.setContent(content1);
        createArtifact.setFirstVersion(v1);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Register second version with semantic version "v2.0.0"
        CreateVersion v2 = new CreateVersion();
        v2.setVersion("v2.0.0");
        VersionContent content2 = new VersionContent();
        content2.setContent(schemaV2);
        content2.setContentType(ContentTypes.APPLICATION_JSON);
        v2.setContent(content2);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(v2);

        // ccompat version listing should return [1, 2], not the semantic version strings
        var versionsResponse = given()
                .when()
                .get("/ccompat/v7/subjects/{subject}/versions", artifactId)
                .then()
                .statusCode(200)
                .extract()
                .as(int[].class);
        Assertions.assertEquals(2, versionsResponse.length);
        Assertions.assertEquals(1, versionsResponse[0]);
        Assertions.assertEquals(2, versionsResponse[1]);

        // Both version 1 and version 2 should be resolvable via ccompat
        given()
                .when()
                .get("/ccompat/v7/subjects/{subject}/versions/{version}", artifactId, 1)
                .then()
                .statusCode(200);
        given()
                .when()
                .get("/ccompat/v7/subjects/{subject}/versions/{version}", artifactId, 2)
                .then()
                .statusCode(200);
    }
}
