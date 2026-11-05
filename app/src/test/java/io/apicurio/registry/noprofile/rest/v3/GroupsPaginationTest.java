package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.v3.beans.EditableContractMetadata;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Regression tests for issue #9268: the v3 GroupsResource list endpoints must normalize negative
 * and overflowing offset/limit values instead of forwarding them to storage, matching the v3
 * search endpoints.
 */
@QuarkusTest
public class GroupsPaginationTest extends AbstractResourceTestBase {

    private static final String AVRO_SCHEMA =
            "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}";
    private static final long OVER_INT_MAX = 2147483648L;

    @Test
    public void testListGroups() throws Exception {
        // listGroups has no filter and other tests share the registry, so only relative sizes
        // are asserted here.
        for (int idx = 0; idx < 2; idx++) {
            CreateGroup createGroup = new CreateGroup();
            createGroup.setGroupId("GroupsPaginationTest_" + UUID.randomUUID());
            clientV3.groups().post(createGroup);
        }
        String path = "/registry/v3/groups";

        given().queryParam("limit", -1).get(path).then().statusCode(200).body("groups.size()", equalTo(1));
        given().queryParam("offset", -1).queryParam("limit", 1).get(path).then().statusCode(200)
                .body("groups.size()", equalTo(1));
        given().queryParam("offset", OVER_INT_MAX).get(path).then().statusCode(200)
                .body("groups.size()", equalTo(0));
        given().queryParam("limit", OVER_INT_MAX).get(path).then().statusCode(200);
    }

    @Test
    public void testListArtifactsInGroup() throws Exception {
        String groupId = UUID.randomUUID().toString();
        for (int idx = 0; idx < 3; idx++) {
            createArtifact(groupId, "artifact-" + idx, ArtifactType.AVRO, AVRO_SCHEMA,
                    ContentTypes.APPLICATION_JSON);
        }

        assertPagination(given().pathParam("groupId", groupId), "/registry/v3/groups/{groupId}/artifacts",
                "artifacts", 3);
    }

    @Test
    public void testListArtifactVersions() throws Exception {
        String groupId = UUID.randomUUID().toString();
        String artifactId = "versions";
        createArtifact(groupId, artifactId, ArtifactType.AVRO, AVRO_SCHEMA, ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, AVRO_SCHEMA.replace("\"x\"", "\"y\""),
                ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, AVRO_SCHEMA.replace("\"x\"", "\"z\""),
                ContentTypes.APPLICATION_JSON);

        assertPagination(given().pathParam("groupId", groupId).pathParam("artifactId", artifactId),
                "/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions", "versions", 3);
    }

    @Test
    public void testListBranchesAndBranchVersions() throws Exception {
        String groupId = UUID.randomUUID().toString();
        String artifactId = "branches";
        createArtifact(groupId, artifactId, ArtifactType.AVRO, AVRO_SCHEMA, ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, AVRO_SCHEMA.replace("\"x\"", "\"y\""),
                ContentTypes.APPLICATION_JSON);

        String branchesPath = "/registry/v3/groups/{groupId}/artifacts/{artifactId}/branches";
        int branchCount = given().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .get(branchesPath).then().statusCode(200).extract().path("count");
        assertPagination(given().pathParam("groupId", groupId).pathParam("artifactId", artifactId),
                branchesPath, "branches", branchCount);

        // The system "latest" branch holds both versions.
        assertPagination(given().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .pathParam("branchId", "latest"),
                "/registry/v3/groups/{groupId}/artifacts/{artifactId}/branches/{branchId}/versions",
                "versions", 2);
    }

    @Test
    public void testListContracts() throws Exception {
        String groupId = UUID.randomUUID().toString();
        String path = "/registry/v3/groups/{groupId}/contracts";
        createArtifact(groupId, "schema", ArtifactType.AVRO, AVRO_SCHEMA, ContentTypes.APPLICATION_JSON);
        for (int idx = 0; idx < 2; idx++) {
            given().header("Content-Type", "application/x-yaml").pathParam("groupId", groupId)
                    .body(odcsContract(groupId, "schema").getBytes()).post(path).then().statusCode(200);
        }

        given().pathParam("groupId", groupId).queryParam("limit", -1).get(path).then().statusCode(200)
                .body("size()", equalTo(1));
        given().pathParam("groupId", groupId).queryParam("offset", -1).get(path).then().statusCode(200)
                .body("size()", equalTo(2));
    }

    @Test
    public void testGetContractAuditLog() throws Exception {
        String groupId = UUID.randomUUID().toString();
        String artifactId = "audited";
        createArtifact(groupId, artifactId, ArtifactType.AVRO, AVRO_SCHEMA, ContentTypes.APPLICATION_JSON);
        for (String team : new String[] { "team-a", "team-b" }) {
            given().contentType(CT_JSON).pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                    .body(EditableContractMetadata.builder().status(EditableContractMetadata.Status.DRAFT)
                            .ownerTeam(team).build())
                    .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata").then()
                    .statusCode(200);
        }

        String path = "/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/audit";
        int entries = given().pathParam("groupId", groupId).pathParam("artifactId", artifactId).get(path)
                .then().statusCode(200).extract().path("size()");

        given().pathParam("groupId", groupId).pathParam("artifactId", artifactId).queryParam("limit", -1)
                .get(path).then().statusCode(200).body("size()", equalTo(1));
        given().pathParam("groupId", groupId).pathParam("artifactId", artifactId).queryParam("offset", -1)
                .get(path).then().statusCode(200).body("size()", equalTo(entries));
        given().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .queryParam("offset", OVER_INT_MAX).get(path).then().statusCode(200).body("size()", equalTo(0));
        given().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .queryParam("limit", OVER_INT_MAX).get(path).then().statusCode(200)
                .body("size()", equalTo(entries));
    }

    /**
     * Checks negative, overflowing and normal offset/limit values against an endpoint whose
     * result list (under {@code listField}) has exactly {@code total} entries.
     */
    private void assertPagination(RequestSpecification spec, String path, String listField, int total) {
        String size = listField + ".size()";
        // A negative limit is normalized to 1.
        given().spec(spec).queryParam("limit", -1).get(path).then().statusCode(200)
                .body("count", equalTo(total)).body(size, equalTo(1));
        // A negative offset is normalized to 0.
        given().spec(spec).queryParam("offset", -1).get(path).then().statusCode(200)
                .body("count", equalTo(total)).body(size, equalTo(total));
        // limit=0 keeps its empty-page semantics.
        given().spec(spec).queryParam("limit", 0).get(path).then().statusCode(200)
                .body("count", equalTo(total)).body(size, equalTo(0));
        // Values above Integer.MAX_VALUE are clamped instead of wrapping to a negative int.
        given().spec(spec).queryParam("offset", OVER_INT_MAX).get(path).then().statusCode(200)
                .body("count", equalTo(total)).body(size, equalTo(0));
        given().spec(spec).queryParam("limit", OVER_INT_MAX).get(path).then().statusCode(200)
                .body("count", equalTo(total)).body(size, equalTo(total));
    }

    private String odcsContract(String schemaGroupId, String schemaArtifactId) {
        return "apiVersion: v3.1.0\n"
                + "kind: DataContract\n"
                + "id: test-contract-" + UUID.randomUUID() + "\n"
                + "info:\n"
                + "  title: Test Contract\n"
                + "  version: 1.0.0\n"
                + "  status: active\n"
                + "schemas:\n"
                + "  - name: R\n"
                + "    type: avro\n"
                + "    location: " + schemaGroupId + "/" + schemaArtifactId + ":latest\n";
    }
}
