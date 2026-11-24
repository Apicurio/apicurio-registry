package io.apicurio.registry.noprofile.ccompat.rest.v7;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class CompatibilityResourceTest extends AbstractResourceTestBase {

    private static final String AVRO_CONTENT_V1 = """
            {
               "type" : "record",
               "namespace" : "Apicurio",
               "name" : "FullName",
               "fields" : [
                  { "name" : "FirstName" , "type" : "string" },
                  { "name" : "LastName" , "type" : "string" }
               ]
            }
            """;

    private static final String AVRO_CONTENT_V2 = """
            {
               "type" : "record",
               "namespace" : "Apicurio",
               "name" : "FullName",
               "fields" : [
                  { "name" : "FirstName" , "type" : "string" },
                  { "name" : "MiddleName" , "type" : "string" },
                  { "name" : "LastName" , "type" : "string" }
               ]
            }
            """;

    @Test
    public void testUnknownLatestSchemaForSubject() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("schema", "{}");
        requestBody.put("schemaType", "AVRO");

        String subject = "unknown";
        String version = "1";
        given().when()
                    .contentType(CT_JSON)
                    .body(requestBody)
                    .post("/ccompat/v7/compatibility/subjects/{subject}/versions/{version}", subject, version)
                .then()
                    .statusCode(404)
                    .body(anything());

        subject = "unknown";
        version = "latest";
        given().when()
                    .contentType(CT_JSON)
                    .body(requestBody)
                    .post("/ccompat/v7/compatibility/subjects/{subject}/versions/{version}", subject, version)
                .then()
                    .statusCode(200)
                    .body(anything());
    }

    @Test
    public void testDraftVersionsAreNotUsedForCompatibilityCheck() throws Exception {
        String subject = TestUtils.generateArtifactId();
        String groupId = GroupId.DEFAULT.getRawGroupIdWithDefaultString();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(subject, ArtifactType.AVRO,
                AVRO_CONTENT_V2, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig("BACKWARD");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(subject).rules().post(createRule);

        // The draft is not a published version and must not be used as a compatibility baseline.
        CreateVersion draft = TestUtils.clientCreateVersion(AVRO_CONTENT_V1, ContentTypes.APPLICATION_JSON);
        draft.setVersion("2.0");
        draft.setIsDraft(true);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(subject).versions().post(draft);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("schema", AVRO_CONTENT_V2);
        requestBody.put("schemaType", "AVRO");

        given().when()
                    .contentType(CT_JSON)
                    .body(requestBody)
                    .post("/ccompat/v7/compatibility/subjects/{subject}/versions", subject)
                .then()
                    .statusCode(200)
                    .body("is_compatible", is(true));
    }

    @Test
    public void testDraftOnlySubjectUsesEmptyBaseline() throws Exception {
        String subject = TestUtils.generateArtifactId();
        String groupId = GroupId.DEFAULT.getRawGroupIdWithDefaultString();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(subject, ArtifactType.AVRO,
                AVRO_CONTENT_V1, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0");
        createArtifact.getFirstVersion().setIsDraft(true);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("schema", AVRO_CONTENT_V2);
        requestBody.put("schemaType", "AVRO");

        given().when()
                    .contentType(CT_JSON)
                    .body(requestBody)
                    .post("/ccompat/v7/compatibility/subjects/{subject}/versions", subject)
                .then()
                    .statusCode(200)
                    .body("is_compatible", is(true));
    }

    @Test
    public void testMalformedSchemaIsRejectedForDraftOnlySubject() throws Exception {
        String subject = TestUtils.generateArtifactId();
        String groupId = GroupId.DEFAULT.getRawGroupIdWithDefaultString();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(subject, ArtifactType.AVRO,
                AVRO_CONTENT_V1, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0");
        createArtifact.getFirstVersion().setIsDraft(true);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("schema", "{ \"type\": \"record\",");
        requestBody.put("schemaType", "AVRO");

        given().when()
                    .contentType(CT_JSON)
                    .body(requestBody)
                    .post("/ccompat/v7/compatibility/subjects/{subject}/versions", subject)
                .then()
                    .statusCode(422);
    }

    @Test
    public void testSunsetVersionIsUsedForCompatibilityCheck() throws Exception {
        String subject = TestUtils.generateArtifactId();
        String groupId = GroupId.DEFAULT.getRawGroupIdWithDefaultString();

        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(subject, ArtifactType.AVRO,
                AVRO_CONTENT_V1, ContentTypes.APPLICATION_JSON);
        createArtifact.getFirstVersion().setVersion("1.0");
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig("BACKWARD");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(subject).rules().post(createRule);

        WrappedVersionState sunset = new WrappedVersionState();
        sunset.setState(VersionState.SUNSET);
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(subject).versions()
                .byVersionExpression("1.0").state().put(sunset);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("schema", AVRO_CONTENT_V2);
        requestBody.put("schemaType", "AVRO");

        // The v3 baseline includes SUNSET versions, so the candidate must be checked against this one.
        given().when()
                    .contentType(CT_JSON)
                    .body(requestBody)
                    .post("/ccompat/v7/compatibility/subjects/{subject}/versions", subject)
                .then()
                    .statusCode(200)
                    .body("is_compatible", is(false));
    }

    @Test
    public void testCompatibilityCheckForUnknownSubject() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("schema", AVRO_CONTENT_V1);
        requestBody.put("schemaType", "AVRO");

        given().when()
                    .contentType(CT_JSON)
                    .body(requestBody)
                    .post("/ccompat/v7/compatibility/subjects/{subject}/versions",
                            "unknown-" + TestUtils.generateArtifactId())
                .then()
                    .statusCode(404);
    }
}
