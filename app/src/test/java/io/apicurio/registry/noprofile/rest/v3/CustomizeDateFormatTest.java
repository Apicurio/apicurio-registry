package io.apicurio.registry.noprofile.rest.v3;


import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class CustomizeDateFormatTest extends AbstractResourceTestBase {

    private static final String GROUP = "CustomizeDateFormatTest";

    private static final String dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    SimpleDateFormat compliantPattern;

    @BeforeAll
    public void init() {
        compliantPattern = new SimpleDateFormat(dateFormat);
    }

    @Test
    public void testOpenApiCompliantDateFormat() throws Exception {

        String artifactContent = resourceToString("openapi-empty.json");

        // Create OpenAPI artifact
        createArtifact(GROUP, "testGetArtifactMetaData/EmptyAPI", ArtifactType.OPENAPI, artifactContent);

        // Get the artifact meta-data
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", "testGetArtifactMetaData/EmptyAPI")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/meta")
                .then()
                .statusCode(200)
                .body("createdOn", new BaseMatcher<Object>() {

                    @Override
                    public void describeTo(Description description) {

                    }

                    @Override
                    public boolean matches(Object o) {
                        try {
                            compliantPattern.parse(o.toString());
                            return true;
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }
}
