package io.apicurio.registry;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(DataUpgradeTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class DataUpgradeTest extends AbstractResourceTestBase {

    @Override
    @BeforeEach
    protected void beforeEach() throws Exception {
        setupRestAssured();
    }

    @Test
    public void testCheckImportedData() throws Exception {
        given().when().accept(CT_JSON).get("/registry/v3/search/artifacts").then().statusCode(200)
                .body("count", is(26));
    }
}
