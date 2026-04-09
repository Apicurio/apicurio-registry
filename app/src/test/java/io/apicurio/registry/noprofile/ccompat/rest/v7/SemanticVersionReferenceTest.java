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
 * Test to verify that the ccompat API correctly handles schema references to artifacts
 * with non-integer (semantic) version strings.
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/7694">Issue #7694</a>
 */
@QuarkusTest
public class SemanticVersionReferenceTest extends AbstractResourceTestBase {

    /**
     * Tests that fetching a schema via the ccompat API works when the schema has references
     * to artifacts with semantic (non-integer) version strings.
     */
    @Test
    public void testSchemaReferencesWithSemanticVersion() throws Exception {
        final String groupId = "default";
        final String referencedArtifactId = TestUtils.generateArtifactId();
        final String referencingArtifactId = TestUtils.generateArtifactId();

        // Referenced schema: a simple Avro record
        final String referencedSchema = "{\"type\":\"record\",\"name\":\"Subrecord\","
                + "\"namespace\":\"otherns\",\"fields\":"
                + "[{\"name\":\"field2\",\"type\":\"string\"}]}";

        // Create the referenced artifact with a semantic version (non-integer)
        CreateArtifact createReferenced = new CreateArtifact();
        createReferenced.setArtifactId(referencedArtifactId);
        createReferenced.setArtifactType(ArtifactType.AVRO);
        CreateVersion referencedVersion = new CreateVersion();
        referencedVersion.setVersion("v5.1.1");
        VersionContent referencedContent = new VersionContent();
        referencedContent.setContent(referencedSchema);
        referencedContent.setContentType(ContentTypes.APPLICATION_JSON);
        referencedVersion.setContent(referencedContent);
        createReferenced.setFirstVersion(referencedVersion);
        clientV3.groups().byGroupId(groupId).artifacts().post(createReferenced);

        // Referencing schema: references the Subrecord type
        final String referencingSchema = "{\"type\":\"record\",\"name\":\"MyRecord\","
                + "\"namespace\":\"ns\",\"fields\":"
                + "[{\"name\":\"field1\",\"type\":\"otherns.Subrecord\"}]}";

        // Create the referencing artifact with a reference to the semantic-versioned artifact
        ArtifactReference ref = new ArtifactReference();
        ref.setGroupId(groupId);
        ref.setArtifactId(referencedArtifactId);
        ref.setVersion("v5.1.1");
        ref.setName("otherns.Subrecord");

        CreateArtifact createReferencing = new CreateArtifact();
        createReferencing.setArtifactId(referencingArtifactId);
        createReferencing.setArtifactType(ArtifactType.AVRO);
        CreateVersion referencingVersion = new CreateVersion();
        VersionContent referencingContent = new VersionContent();
        referencingContent.setContent(referencingSchema);
        referencingContent.setContentType(ContentTypes.APPLICATION_JSON);
        referencingContent.setReferences(List.of(ref));
        referencingVersion.setContent(referencingContent);
        createReferencing.setFirstVersion(referencingVersion);
        var referencingResult = clientV3.groups().byGroupId(groupId).artifacts().post(createReferencing);

        long contentId = referencingResult.getVersion().getContentId();

        // Fetch the schema via ccompat API - this should NOT throw a 500
        io.apicurio.registry.ccompat.rest.v7.beans.Schema ccompatSchema = given()
                .when()
                .get("/ccompat/v7/schemas/ids/{id}", contentId)
                .then()
                .statusCode(200)
                .extract()
                .as(io.apicurio.registry.ccompat.rest.v7.beans.Schema.class);

        Assertions.assertNotNull(ccompatSchema, "Schema should be returned from ccompat API");
        Assertions.assertNotNull(ccompatSchema.getReferences(), "Schema should have references");
        Assertions.assertEquals(1, ccompatSchema.getReferences().size(),
                "Schema should have exactly one reference");

        // Verify the reference has a valid integer version (versionOrder=1)
        io.apicurio.registry.ccompat.rest.v7.beans.SchemaReference schemaRef =
                ccompatSchema.getReferences().get(0);
        Assertions.assertEquals(referencedArtifactId, schemaRef.getSubject());
        Assertions.assertEquals("otherns.Subrecord", schemaRef.getName());
        Assertions.assertEquals(1, schemaRef.getVersion().intValue(),
                "Reference version should be the versionOrder (1), not the semantic version string");
    }
}
