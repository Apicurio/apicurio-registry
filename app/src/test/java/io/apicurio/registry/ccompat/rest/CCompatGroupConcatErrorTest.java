package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(GroupConcatProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class CCompatGroupConcatErrorTest extends AbstractResourceTestBase {

    @Test
    public void testSubjectWithoutSeparatorReturnsConfluentErrorBody() {
        given().get("/ccompat/v7/subjects/no-separator/versions").then().statusCode(400)
                .contentType(CT_JSON)
                .body("error_code", equalTo(400))
                .body("message", equalTo("Invalid subject format.  Should be:  groupId:artifactId"));
    }
}
