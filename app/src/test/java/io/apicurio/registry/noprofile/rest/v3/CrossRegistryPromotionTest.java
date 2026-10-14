package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
public class CrossRegistryPromotionTest extends AbstractResourceTestBase {

    private static final String AVRO_V1 = "{\"type\":\"record\",\"name\":\"User\",\"fields\":["
            + "{\"name\":\"id\",\"type\":\"int\"}]}";
    private static final String AVRO_V2 = "{\"type\":\"record\",\"name\":\"User\",\"fields\":["
            + "{\"name\":\"id\",\"type\":\"int\"},"
            + "{\"name\":\"email\",\"type\":\"string\",\"default\":\"\"}]}";

    @Test
    public void testListPromotionSources() {
        given().when().get("/registry/v3/system/promotion/sources").then().statusCode(200)
                .body("name", hasItem("local"));
    }

    @Test
    public void testUnknownSourceRejected() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();
        given().contentType(CT_JSON)
                .body("{\"source\":\"does-not-exist\"}")
                .post("/registry/v3/groups/" + groupId + "/artifacts/" + artifactId + "/promotion/compare")
                .then().statusCode(400);
    }

    @Test
    public void testCompareAndPromoteFromLocalSource() throws Exception {
        String sourceGroup = TestUtils.generateGroupId();
        String targetGroup = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createAvro(sourceGroup, artifactId, AVRO_V1);
        createVersion(sourceGroup, artifactId, AVRO_V2);
        createAvro(targetGroup, artifactId, AVRO_V1);

        given().contentType(CT_JSON)
                .body("{\"source\":\"local\",\"sourceGroupId\":\"" + sourceGroup
                        + "\",\"sourceVersion\":\"2\"}")
                .post("/registry/v3/groups/" + targetGroup + "/artifacts/" + artifactId + "/promotion/compare")
                .then().statusCode(200)
                .body("identical", equalTo(false))
                .body("source.version", equalTo("2"))
                .body("target.version", equalTo("1"))
                .body("compatibility.compatible", equalTo(true));

        given().contentType(CT_JSON)
                .body("{\"source\":\"local\",\"sourceGroupId\":\"" + sourceGroup
                        + "\",\"sourceVersion\":\"2\"}")
                .post("/registry/v3/groups/" + targetGroup + "/artifacts/" + artifactId + "/promotion")
                .then().statusCode(200)
                .body("alreadyPromoted", equalTo(false))
                .body("version.version", equalTo("2"))
                .body("version.labels['apicurio.promotion.source']", equalTo("local"));

        given().contentType(CT_JSON)
                .body("{\"source\":\"local\",\"sourceGroupId\":\"" + sourceGroup
                        + "\",\"sourceVersion\":\"2\"}")
                .post("/registry/v3/groups/" + targetGroup + "/artifacts/" + artifactId + "/promotion")
                .then().statusCode(200)
                .body("alreadyPromoted", equalTo(true))
                .body("version.version", equalTo("2"));
    }

    @Test
    public void testPromoteCreatesArtifactWhenMissing() throws Exception {
        String sourceGroup = TestUtils.generateGroupId();
        String targetGroup = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        createAvro(sourceGroup, artifactId, AVRO_V1);

        given().contentType(CT_JSON)
                .body("{\"source\":\"local\",\"sourceGroupId\":\"" + sourceGroup + "\"}")
                .post("/registry/v3/groups/" + targetGroup + "/artifacts/" + artifactId + "/promotion")
                .then().statusCode(200)
                .body("alreadyPromoted", equalTo(false))
                .body("version.artifactId", equalTo(artifactId))
                .body("compare.target", nullValue());
    }

    private void createAvro(String groupId, String artifactId, String content) throws Exception {
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO, content,
                ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    private void createVersion(String groupId, String artifactId, String content) throws Exception {
        CreateVersion createVersion = TestUtils.clientCreateVersion(content, ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion);
    }
}
