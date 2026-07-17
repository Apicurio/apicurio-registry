package io.apicurio.registry.headers;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.ProxyHeaderAuthTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

// #2411: 401/403 flow through the normal filter chain (unlike the disabled-API 404), so HSTSFilter
// already covers them - this locks that in.
@QuarkusTest
@TestProfile(ProxyHeaderAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class HstsAuthHeaderTest extends AbstractResourceTestBase {

    private static final String HSTS = "Strict-Transport-Security";
    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    private static final String HEADER_USERNAME = "X-Forwarded-User";
    private static final String HEADER_EMAIL = "X-Forwarded-Email";

    @Override
    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // Skip: the base-class cleanup would fail with a 401 here, since it sends no proxy auth headers.
    }

    @Test
    public void testHstsOnUnauthorized401() {
        given().when().get("/registry/v3/search/artifacts").then().statusCode(401).header(HSTS,
                containsString("max-age="));
    }

    @Test
    public void testHstsOnForbidden403() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Authenticated but no roles (no groups header) -> 403.
        given().header(HEADER_USERNAME, "testuser").header(HEADER_EMAIL, "testuser@example.com")
                .contentType(ContentType.JSON)
                .body(TestUtils.clientCreateArtifact(artifactId, ArtifactType.JSON, ARTIFACT_CONTENT,
                        ContentTypes.APPLICATION_JSON))
                .when().pathParam("groupId", groupId).post("/registry/v3/groups/{groupId}/artifacts").then()
                .statusCode(403).header(HSTS, containsString("max-age="));
    }
}
