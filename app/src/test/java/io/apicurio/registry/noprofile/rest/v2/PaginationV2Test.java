package io.apicurio.registry.noprofile.rest.v2;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Regression tests for issue #9369: the v2 pagination endpoints must normalize negative and
 * overflowing offset/limit values instead of forwarding them to storage, matching the behavior
 * already covered for the v3 API in SearchArtifactsTest, SearchVersionsTest, and SearchGroupsTest.
 */
@QuarkusTest
public class PaginationV2Test extends AbstractResourceTestBase {

    @Test
    public void testSearchArtifactsLimitAndOffsetEdgeCases() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        for (int idx = 0; idx < 3; idx++) {
            String artifactId = "Empty-" + idx;
            String name = "empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI,
                    artifactContent.replaceAll("Empty API", name), ContentTypes.APPLICATION_JSON);
        }

        // A negative limit is normalized to 1, not passed to storage as an invalid query (#9369).
        given().when().queryParam("group", group).queryParam("limit", -1)
                .get("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(1));

        // A negative offset is normalized to 0, returning the full result set.
        given().when().queryParam("group", group).queryParam("offset", -1)
                .get("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(3));

        // Both parameters invalid at once: offset -> 0, limit -> 1.
        given().when().queryParam("group", group).queryParam("offset", -1).queryParam("limit", -1)
                .get("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(1));

        // limit=0 keeps its current semantics (empty page); only negative values are normalized.
        given().when().queryParam("group", group).queryParam("limit", 0)
                .get("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(0));

        // Boundary values: offset=0 and limit=1 are valid and behave normally.
        given().when().queryParam("group", group).queryParam("offset", 0)
                .get("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(3));
        given().when().queryParam("group", group).queryParam("limit", 1)
                .get("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(1));

        // Omitted offset/limit fall back to the endpoint's defaults (0 / 20).
        given().when().queryParam("group", group).get("/registry/v2/search/artifacts").then()
                .statusCode(200).body("count", equalTo(3)).body("artifacts.size()", equalTo(3));

        // Oversized values are clamped to Integer.MAX_VALUE instead of wrapping to a negative int,
        // which would have produced a 500. Unlike v3, v2 does not cap limit at a fixed maximum.
        given().when().queryParam("group", group).queryParam("offset", 2147483648L)
                .get("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(0));
        given().when().queryParam("group", group).queryParam("limit", 2147483648L)
                .get("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(3));
    }

    @Test
    public void testSearchArtifactsByContentLimitAndOffsetEdgeCases() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json")
                .replaceAll("Empty API", "PaginationV2Test-searchByContent");

        for (int idx = 0; idx < 3; idx++) {
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent,
                    ContentTypes.APPLICATION_JSON);
        }

        // A negative offset is normalized to 0, returning the full result set.
        given().when().body(artifactContent).queryParam("offset", -1)
                .post("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(3));

        // A negative limit is normalized to 1, not passed to storage as an invalid query (#9369).
        given().when().body(artifactContent).queryParam("limit", -1)
                .post("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(1));

        // Both parameters invalid at once: offset -> 0, limit -> 1.
        given().when().body(artifactContent).queryParam("offset", -1).queryParam("limit", -1)
                .post("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(1));

        // limit=0 keeps its current semantics (empty page); only negative values are normalized.
        given().when().body(artifactContent).queryParam("limit", 0)
                .post("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(0));

        // Omitted offset/limit fall back to the endpoint's defaults (0 / 20).
        given().when().body(artifactContent).post("/registry/v2/search/artifacts").then()
                .statusCode(200).body("count", equalTo(3)).body("artifacts.size()", equalTo(3));

        // Oversized values are capped instead of wrapping to a negative int, which would have
        // produced a 500.
        given().when().body(artifactContent).queryParam("offset", 2147483648L)
                .post("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(0));
        given().when().body(artifactContent).queryParam("limit", 2147483648L)
                .post("/registry/v2/search/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(3));
    }

    @Test
    public void testListGroupsLimitAndOffsetEdgeCases() throws Exception {
        // listGroups (v2) has no group-id filter, unlike the v3 search endpoints, so this test
        // cannot isolate an exact total count the way SearchGroupsTest (v3) does: other tests
        // running in the same JVM may also have created groups. Instead, the assertions below
        // rely only on relative/clamped sizes, which hold regardless of how many other groups
        // exist, while still proving the fix (a negative or overflowing value no longer causes a
        // 500 and pagination is still bounded correctly).
        for (int idx = 0; idx < 3; idx++) {
            CreateGroup createGroup = new CreateGroup();
            createGroup.setGroupId("PaginationV2Test_listGroups_" + UUID.randomUUID());
            clientV3.groups().post(createGroup);
        }

        // A negative limit is normalized to 1, not passed to storage as an invalid query (#9369).
        given().when().queryParam("limit", -1).get("/registry/v2/groups").then().statusCode(200)
                .body("groups.size()", equalTo(1));

        // limit=0 keeps its current semantics (empty page); only negative values are normalized.
        given().when().queryParam("limit", 0).get("/registry/v2/groups").then().statusCode(200)
                .body("groups.size()", equalTo(0));

        // A negative offset is normalized to 0: identical to an explicit offset=0 request.
        int baselineSize = given().when().queryParam("offset", 0).queryParam("limit", 20)
                .get("/registry/v2/groups").then().statusCode(200).extract()
                .path("groups.size()");
        given().when().queryParam("offset", -1).queryParam("limit", 20)
                .get("/registry/v2/groups").then().statusCode(200)
                .body("groups.size()", equalTo(baselineSize));

        // Both parameters invalid at once: offset -> 0, limit -> 1.
        given().when().queryParam("offset", -1).queryParam("limit", -1)
                .get("/registry/v2/groups").then().statusCode(200)
                .body("groups.size()", equalTo(1));

        // Omitted offset/limit fall back to the endpoint's defaults (0 / 20) - must not 500.
        given().when().get("/registry/v2/groups").then().statusCode(200);

        // An oversized offset is capped at Integer.MAX_VALUE instead of wrapping to a negative
        // int, which would have produced a 500; no real deployment has that many groups.
        given().when().queryParam("offset", 2147483648L).get("/registry/v2/groups").then()
                .statusCode(200).body("groups.size()", equalTo(0));

        // An oversized limit is clamped to Integer.MAX_VALUE instead of wrapping to a negative
        // int. Unlike v3, v2 does not cap limit at a fixed maximum.
        given().when().queryParam("limit", 2147483648L).get("/registry/v2/groups").then()
                .statusCode(200);
    }

    @Test
    public void testListArtifactsInGroupLimitAndOffsetEdgeCases() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactContent = resourceToString("openapi-empty.json");

        for (int idx = 0; idx < 3; idx++) {
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent,
                    ContentTypes.APPLICATION_JSON);
        }

        // A negative limit is normalized to 1, not passed to storage as an invalid query (#9369).
        given().when().pathParam("groupId", group).queryParam("limit", -1)
                .get("/registry/v2/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(1));

        // A negative offset is normalized to 0, returning the full result set.
        given().when().pathParam("groupId", group).queryParam("offset", -1)
                .get("/registry/v2/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(3));

        // Both parameters invalid at once: offset -> 0, limit -> 1.
        given().when().pathParam("groupId", group).queryParam("offset", -1)
                .queryParam("limit", -1).get("/registry/v2/groups/{groupId}/artifacts").then()
                .statusCode(200).body("count", equalTo(3)).body("artifacts.size()", equalTo(1));

        // limit=0 keeps its current semantics (empty page); only negative values are normalized.
        given().when().pathParam("groupId", group).queryParam("limit", 0)
                .get("/registry/v2/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(0));

        // Omitted offset/limit fall back to the endpoint's defaults (0 / 20).
        given().when().pathParam("groupId", group).get("/registry/v2/groups/{groupId}/artifacts")
                .then().statusCode(200).body("count", equalTo(3))
                .body("artifacts.size()", equalTo(3));

        // Oversized values are capped instead of wrapping to a negative int, which would have
        // produced a 500.
        given().when().pathParam("groupId", group).queryParam("offset", 2147483648L)
                .get("/registry/v2/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(0));
        given().when().pathParam("groupId", group).queryParam("limit", 2147483648L)
                .get("/registry/v2/groups/{groupId}/artifacts").then().statusCode(200)
                .body("count", equalTo(3)).body("artifacts.size()", equalTo(3));
    }

    @Test
    public void testListArtifactVersionsLimitAndOffsetEdgeCases() throws Exception {
        String group = UUID.randomUUID().toString();
        String artifactId = "PaginationV2Test-versions";
        String artifactContent = resourceToString("openapi-empty.json");

        this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent,
                ContentTypes.APPLICATION_JSON);
        this.createArtifactVersion(group, artifactId, artifactContent, ContentTypes.APPLICATION_JSON);
        this.createArtifactVersion(group, artifactId, artifactContent, ContentTypes.APPLICATION_JSON);

        // A negative limit is normalized to 1, not passed to storage as an invalid query (#9369).
        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("limit", -1)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("count", equalTo(3)).body("versions.size()", equalTo(1));

        // A negative offset is normalized to 0, returning the full result set.
        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("offset", -1)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("count", equalTo(3)).body("versions.size()", equalTo(3));

        // Both parameters invalid at once: offset -> 0, limit -> 1.
        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("offset", -1).queryParam("limit", -1)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("count", equalTo(3)).body("versions.size()", equalTo(1));

        // limit=0 keeps its current semantics (empty page); only negative values are normalized.
        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("limit", 0)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("count", equalTo(3)).body("versions.size()", equalTo(0));

        // Omitted offset/limit fall back to the endpoint's defaults (0 / 20).
        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("count", equalTo(3)).body("versions.size()", equalTo(3));

        // Oversized values are capped instead of wrapping to a negative int, which would have
        // produced a 500.
        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("offset", 2147483648L)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("count", equalTo(3)).body("versions.size()", equalTo(0));
        given().when().pathParam("groupId", group).pathParam("artifactId", artifactId)
                .queryParam("limit", 2147483648L)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(200).body("count", equalTo(3)).body("versions.size()", equalTo(3));

        // A non-existent artifact must still 404 before pagination normalization runs, confirming
        // the fix did not reorder the pre-existing artifact-existence check.
        given().when().pathParam("groupId", group).pathParam("artifactId", "does-not-exist")
                .queryParam("offset", -1)
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/versions").then()
                .statusCode(404);
    }

}
