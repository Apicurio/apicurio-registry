package io.apicurio.registry.noprofile.compatibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class McpToolPublicationCompatibilityTest extends AbstractResourceTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({"BACKWARD,inputSchema,5,20", "FORWARD,inputSchema,20,5",
            "BACKWARD,outputSchema,20,5", "FORWARD,outputSchema,5,20", "FULL,inputSchema,5,10",
            "BACKWARD_TRANSITIVE,outputSchema,20,5", "FORWARD_TRANSITIVE,inputSchema,20,5",
            "FULL_TRANSITIVE,outputSchema,20,10"})
    void rejectsBreakingPublicationAndPreservesAcceptedVersions(String mode, String side,
            int rejectedLimit, int acceptedLimit) throws Exception {
        String group = "mcp-publication";
        String artifact = generateArtifactId();
        String path = registryV3ApiUrl + "/groups/" + group + "/artifacts/" + artifact;
        String original = tool(side, 10);
        createArtifact(group, artifact, ArtifactType.MCP_TOOL, original, ContentTypes.APPLICATION_JSON);
        createArtifactRule(group, artifact, RuleType.COMPATIBILITY, mode);
        // The Quarkus test database is disposable; artifact deletion is disabled in this profile.
        publish(path, "rejected", tool(side, rejectedLimit))
                .statusCode(400)
                .body("causes.context", hasItem("/" + side + "/properties/query/maxLength"));
        assertVersion(path, "1", original);
        given().get(path + "/versions").then().statusCode(200).body("count", equalTo(1));
        given().get(path + "/versions/rejected").then().statusCode(404);

        String accepted = tool(side, acceptedLimit);
        publish(path, "accepted", accepted).statusCode(200).body("version", equalTo("accepted"));
        assertVersion(path, "accepted", accepted);
        assertVersion(path, "1", original);
        given().get(path + "/versions").then().statusCode(200).body("count", equalTo(2));

        String unsupported = accepted.replace("\"maxLength\":" + acceptedLimit,
                "\"pattern\":\"a.*\"");
        publish(path, "unsupported", unsupported).statusCode(422);
        given().get(path + "/versions/unsupported").then().statusCode(404);
        given().get(path + "/versions").then().statusCode(200).body("count", equalTo(2));
        assertVersion(path, "accepted", accepted);
        assertVersion(path, "1", original);
    }

    @ParameterizedTest
    @CsvSource({"BACKWARD_TRANSITIVE,inputSchema,20,5,10", "FORWARD_TRANSITIVE,inputSchema,5,20,10",
            "BACKWARD_TRANSITIVE,outputSchema,5,20,10", "FORWARD_TRANSITIVE,outputSchema,20,5,10",
            "FULL_TRANSITIVE,inputSchema,20,10,10", "FULL_TRANSITIVE,outputSchema,20,10,10"})
    void publicationChecksOlderVersionsWhenLatestAloneWouldPass(String mode, String side,
            int firstLimit, int latestLimit, int proposedLimit) throws Exception {
        String group = "mcp-publication";
        String artifact = generateArtifactId();
        String path = registryV3ApiUrl + "/groups/" + group + "/artifacts/" + artifact;
        String first = tool(side, firstLimit);
        String latest = tool(side, latestLimit);
        String proposed = tool(side, proposedLimit);
        createArtifact(group, artifact, ArtifactType.MCP_TOOL, first, ContentTypes.APPLICATION_JSON);
        publish(path, "latest", latest).statusCode(200);
        createArtifactRule(group, artifact, RuleType.COMPATIBILITY, mode);
        publish(path, "candidate", proposed).statusCode(400)
                .body("causes.context", hasItem("/" + side + "/properties/query/maxLength"));
        given().get(path + "/versions/candidate").then().statusCode(404);
        given().get(path + "/versions").then().statusCode(200).body("count", equalTo(2));
        assertVersion(path, "1", first);
        assertVersion(path, "latest", latest);
        given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("config", mode.replace("_TRANSITIVE", "")))
                .put(path + "/rules/COMPATIBILITY").then().statusCode(200)
                .body("config", equalTo(mode.replace("_TRANSITIVE", "")));
        publish(path, "candidate", proposed).statusCode(200);
        assertVersion(path, "candidate", proposed);
    }

    private ValidatableResponse publish(String path, String version, String content) {
        return given().contentType(ContentTypes.APPLICATION_JSON)
                .body(Map.of("version", version, "content", Map.of("content", content,
                        "contentType", ContentTypes.APPLICATION_JSON)))
                .post(path + "/versions").then();
    }

    private void assertVersion(String path, String version, String expected) throws Exception {
        String actual = given().get(path + "/versions/" + version + "/content")
                .then().statusCode(200).extract().asString();
        assertEquals(MAPPER.readTree(expected), MAPPER.readTree(actual));
    }

    private static String tool(String side, int maxLength) {
        String schema = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"maxLength\":"
                + maxLength + "}},\"required\":[\"query\"]}";
        return side.equals("inputSchema") ? "{\"name\":\"tool\",\"inputSchema\":" + schema + "}"
                : "{\"name\":\"tool\",\"inputSchema\":{\"type\":\"object\"},\"outputSchema\":" + schema + "}";
    }
}
