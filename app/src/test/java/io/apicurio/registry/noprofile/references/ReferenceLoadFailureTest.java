package io.apicurio.registry.noprofile.references;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A reference that exists but fails to load must fail the request with the storage error, not be skipped as
 * if it did not exist and then surface as an unrelated error: an Avro parse error from the VALIDITY rule, or a
 * claim that the reference points to a version that does not exist (#9626).
 */
@QuarkusTest
@TestProfile(ReferenceLoadFailureTest.FailingReferenceProfile.class)
public class ReferenceLoadFailureTest extends AbstractResourceTestBase {

    /** Enables {@link FailingReferenceStorageDecorator}. */
    public static class FailingReferenceProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.test.failing-reference.enabled", "true");
        }
    }

    private static final String AVRO_MONEY = """
            {"type": "record", "name": "Money", "namespace": "com.example.common",
             "fields": [{"name": "amount", "type": "double"}]}
            """;

    private static final String AVRO_ORDER = """
            {"type": "record", "name": "Order", "namespace": "com.example",
             "fields": [{"name": "total", "type": "com.example.common.Money"}]}
            """;

    private static final String MONEY_REF_NAME = "com.example.common.Money";

    @Inject
    FailingReferenceStorageDecorator failingReferences;

    private String groupId;

    @BeforeEach
    public void createArtifacts() throws Exception {
        groupId = TestUtils.generateGroupId();
        createArtifact(groupId, "money", ArtifactType.AVRO, AVRO_MONEY, ContentTypes.APPLICATION_JSON);
        createArtifactWithReferences(groupId, "order", ArtifactType.AVRO, AVRO_ORDER, ContentTypes.APPLICATION_JSON,
                List.of(ArtifactReference.builder().groupId(groupId).artifactId("money").version("1")
                        .name(MONEY_REF_NAME).build()));
    }

    @AfterEach
    public void stopFailing() {
        failingReferences.reset();
    }

    @Test
    public void validityRuleReportsTheStorageFailureNotAnAvroParseError() {
        createGroupRule(groupId, RuleType.VALIDITY, "FULL");
        failingReferences.failOn(groupId, "money");

        Response response = given().contentType(CT_JSON).body(Map.of("artifactId", "order2", "artifactType",
                ArtifactType.AVRO, "firstVersion", Map.of("version", "1", "content", Map.of("content", AVRO_ORDER,
                        "contentType", ContentTypes.APPLICATION_JSON, "references", List.of(Map.of("groupId",
                                groupId, "artifactId", "money", "version", "1", "name", MONEY_REF_NAME))))))
                .post("/registry/v3/groups/" + groupId + "/artifacts");

        assertStorageFailure(response);
    }

    @Test
    public void dereferenceReportsTheStorageFailureNotAMissingReference() {
        failingReferences.failOn(groupId, "money");

        Response response = given().queryParam("references", "DEREFERENCE")
                .get("/registry/v3/groups/" + groupId + "/artifacts/order/versions/1/content");

        assertStorageFailure(response);
    }

    private static void assertStorageFailure(Response response) {
        assertEquals(500, response.getStatusCode(), response.asString());
        JsonPath problem = response.jsonPath();
        assertEquals("RegistryStorageException", problem.getString("name"), response.asString());
        assertEquals(FailingReferenceStorageDecorator.FAILURE_MESSAGE, problem.getString("title"),
                response.asString());
        assertEquals(500, problem.getInt("status"), response.asString());
    }
}
