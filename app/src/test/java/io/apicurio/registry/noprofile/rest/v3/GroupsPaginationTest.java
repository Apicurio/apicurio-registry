package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateBranch;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Regression tests for the pagination offset/limit clamping in {@code GroupsResourceImpl}: a
 * negative or overflowing value must be normalized instead of forwarded to storage (which used to
 * silently truncate via {@code BigInteger.intValue()}), and omitted values must keep falling back
 * to the endpoint's defaults. Mirrors the coverage added for the v2 endpoints in
 * {@link io.apicurio.registry.noprofile.rest.v2.PaginationV2Test}.
 */
@QuarkusTest
public class GroupsPaginationTest extends AbstractResourceTestBase {

    @Test
    public void testListGroupsLimitAndOffsetEdgeCases() throws Exception {
        String groupPrefix = "GroupsPaginationTest_listGroups_" + UUID.randomUUID();
        for (int idx = 0; idx < 3; idx++) {
            CreateGroup createGroup = new CreateGroup();
            createGroup.setGroupId(groupPrefix + "_" + idx);
            clientV3.groups().post(createGroup);
        }

        given().when().queryParam("limit", -1).get("/registry/v3/groups").then().statusCode(200)
                .body("groups.size()", equalTo(1));

        given().when().queryParam("limit", 0).get("/registry/v3/groups").then().statusCode(200)
                .body("groups.size()", equalTo(0));

        given().when().queryParam("offset", -1).queryParam("limit", -1)
                .get("/registry/v3/groups").then().statusCode(200)
                .body("groups.size()", equalTo(1));

        given().when().get("/registry/v3/groups").then().statusCode(200);

        given().when().queryParam("offset", 2147483648L).get("/registry/v3/groups").then()
                .statusCode(200).body("groups.size()", equalTo(0));


        given().when().queryParam("limit", 2147483648L).get("/registry/v3/groups").then()
                .statusCode(200);
    }

    @Test
    public void testListArtifactsInGroupLimitAndOffsetEdgeCases() throws Exception {
        String group = TestUtils.generateGroupId();
        String artifactContent = resourceToString("openapi-empty.json");

        for (int idx = 0; idx < 3; idx++) {
            String artifactId = "Empty-" + idx;
            createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent,
                    ContentTypes.APPLICATION_JSON);
        }

        given().when().pathParam("groupId", group).queryParam("limit", -1)
                .get("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(1));

        given().when().pathParam("groupId", group).queryParam("offset", -1)
                .get("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(3));

        given().when().pathParam("groupId", group).queryParam("limit", 0)
                .get("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(0));

        given().when().pathParam("groupId", group).get("/registry/v3/groups/{groupId}/artifacts")
                .then().statusCode(200).body("count", equalTo(3))
                .body("artifacts.size()", equalTo(3));

        given().when().pathParam("groupId", group).queryParam("offset", 2147483648L)
                .get("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(0));
        given().when().pathParam("groupId", group).queryParam("limit", 2147483648L)
                .get("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(3));
    }

    @Test
    public void testListArtifactVersionsLimitAndOffsetEdgeCases() throws Exception {
        String group = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        String artifactContent = resourceToString("openapi-empty.json");

        createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        createArtifactVersion(group, artifactId, artifactContent, ContentTypes.APPLICATION_JSON);
        createArtifactVersion(group, artifactId, artifactContent, ContentTypes.APPLICATION_JSON);

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("limit", -1)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("count", equalTo(3)).body("versions.size()", equalTo(1));

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("offset", -1)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("count", equalTo(3)).body("versions.size()", equalTo(3));

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("offset", 2147483648L)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("count", equalTo(3)).body("versions.size()", equalTo(0));

        given().when().pathParam("groupId", group).pathParam("artifactId", "does-not-exist")
                .queryParam("offset", -1)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(404);
    }

    @Test
    public void testListBranchesLimitAndOffsetEdgeCases() throws Exception {
        String group = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        createArtifact(group, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);

        for (String branchId : new String[] { "1.x", "2.x" }) {
            CreateBranch createBranch = new CreateBranch();
            createBranch.setBranchId(branchId);
            clientV3.groups().byGroupId(group).artifacts().byArtifactId(artifactId).branches()
                    .post(createBranch);
        }

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("limit", -1)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/branches").then()
                .statusCode(200).body("count", equalTo(3)).body("branches.size()", equalTo(1));

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("offset", 2147483648L)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/branches").then()
                .statusCode(200).body("count", equalTo(3)).body("branches.size()", equalTo(0));

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/branches").then()
                .statusCode(200).body("count", equalTo(3)).body("branches.size()", equalTo(3));
    }

    @Test
    public void testListBranchVersionsLimitAndOffsetEdgeCases() throws Exception {
        String group = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        createArtifact(group, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        createArtifactVersion(group, artifactId, "{}", ContentTypes.APPLICATION_JSON);

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .pathParam("branchId", "latest").queryParam("limit", -1)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/branches/{branchId}/versions")
                .then().statusCode(200).body("count", equalTo(2))
                .body("versions.size()", equalTo(1));

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .pathParam("branchId", "latest").queryParam("offset", 2147483648L)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/branches/{branchId}/versions")
                .then().statusCode(200).body("count", equalTo(2))
                .body("versions.size()", equalTo(0));

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .pathParam("branchId", "does-not-exist").queryParam("offset", -1)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/branches/{branchId}/versions")
                .then().statusCode(404);
    }

    @Test
    public void testGetContractAuditLogLimitAndOffsetEdgeCases() throws Exception {
        String group = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        createArtifact(group, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/audit")
                .then().statusCode(200);

        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("offset", -1).queryParam("limit", -1)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/audit")
                .then().statusCode(200);
        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("offset", 2147483648L).queryParam("limit", 2147483648L)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/audit")
                .then().statusCode(200);
    }
}
