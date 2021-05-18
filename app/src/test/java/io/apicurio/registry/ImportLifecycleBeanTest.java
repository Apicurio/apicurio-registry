package io.apicurio.registry;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@TestProfile(ImportLifecycleBeanTestProfile.class)
public class ImportLifecycleBeanTest extends AbstractResourceTestBase {

    @BeforeEach
    protected void beforeEach() throws Exception {
        prepareServiceInitializers();
    }

    @Test
    public void testStartupImportGlobalRules() {
        given()
                .when()
                .accept(CT_JSON)
                .get("/registry/v2/admin/rules")
                .then()
                .statusCode(200)
                .body("[0]", equalTo("COMPATIBILITY"))
                .body("[1]", nullValue());
    }

    @Test
    public void testStartupImportArtifacts() {
        given()
                .when()
                .accept(CT_JSON)
                .get("/registry/v2/search/artifacts")
                .then()
                .statusCode(200)
                .body("count", is(3))
                .body("artifacts.id", containsInAnyOrder("Artifact-3", "Artifact-2", "Artifact-1"));
    }

    @Test
    public void testStartupImportArtifactsVersions() {
        given()
                .when()
                .accept(CT_JSON)
                .get("/registry/v2/groups/ImportTest/artifacts/Artifact-1/versions")
                .then()
                .statusCode(200)
                .body("versions.size()", is(3))
                .body("versions[0].version", equalTo("1.0.1"))
                .body("versions[1].version", equalTo("1.0.2"))
                .body("versions[2].version", equalTo("1.0.3"));
    }
}
