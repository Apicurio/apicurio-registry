package io.apicurio.registry.rest;


import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(CustomizeDateFormatTestProfile.class)
public class CustomizeDateFormatTest extends AbstractResourceTestBase {

    private static final String GROUP = "CustomizeDateFormatTest";

    @Inject
    @ConfigProperty(name = "registry.apis.v2.date-format")
    String dateFormat;
    @Inject
    @ConfigProperty(name = "registry.apis.v2.date-format-timezone")
    String timezone;

    SimpleDateFormat newPattern;

    @BeforeAll
    public void init() {
        newPattern = new SimpleDateFormat(dateFormat);
        newPattern.setTimeZone(TimeZone.getTimeZone(timezone));
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
                .get("/registry/v2/groups/{groupId}/artifacts/{artifactId}/meta")
                .then()
                .statusCode(200)
                .body("createdOn", new BaseMatcher<Object>() {

                    @Override
                    public void describeTo(Description description) {

                    }

                    @Override
                    public boolean matches(Object o) {
                        try {
                            newPattern.parse(o.toString());
                            return true;
                        } catch (ParseException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }
}
