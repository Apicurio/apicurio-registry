package io.apicurio.registry.agents.a2a.signature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Map;

/**
 * REST calls shared by the signature verification tests: Agent Card writes, raw so their error bodies
 * can be inspected, and agent discovery.
 */
final class SignedCardRequests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SignedCardRequests() {
    }

    /** An Agent Card whose only skill has this id, so a test can find its own cards. */
    static String card(String skill) {
        return AgentCardSignatureVerifierTest.CARD.replace("\"forecast\"", "\"" + skill + "\"");
    }

    static Response createCard(String groupId, String artifactId, String content, boolean dryRun) {
        return createCard(groupId, artifactId, content, dryRun, false);
    }

    static Response createCard(String groupId, String artifactId, String content, boolean dryRun, boolean draft) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("artifactId", artifactId);
        body.put("artifactType", "AGENT_CARD");
        ObjectNode firstVersion = body.putObject("firstVersion");
        firstVersion.put("version", "1");
        if (draft) {
            firstVersion.put("isDraft", true);
        }
        ObjectNode versionContent = firstVersion.putObject("content");
        versionContent.put("content", content);
        versionContent.put("contentType", "application/json");
        return RestAssured.given().contentType(ContentType.JSON).queryParam("dryRun", dryRun).body(body.toString())
                .post("/registry/v3/groups/{groupId}/artifacts", groupId);
    }

    static Response createVersion(String groupId, String artifactId, String content) {
        ObjectNode body = MAPPER.createObjectNode();
        ObjectNode versionContent = body.putObject("content");
        versionContent.put("content", content);
        versionContent.put("contentType", "application/json");
        return RestAssured.given().contentType(ContentType.JSON).body(body.toString())
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions", groupId, artifactId);
    }

    static Response publishDraft(String groupId, String artifactId, String version) {
        return RestAssured.given().contentType(ContentType.JSON).body("{\"state\":\"ENABLED\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/state", groupId,
                        artifactId, version);
    }

    static Response artifactStatus(String groupId, String artifactId) {
        return RestAssured.given().get("/registry/v3/groups/{groupId}/artifacts/{artifactId}", groupId, artifactId);
    }

    static void setLabels(String groupId, String artifactId, Map<String, String> labels) {
        ObjectNode body = MAPPER.createObjectNode();
        ObjectNode labelsNode = body.putObject("labels");
        labels.forEach(labelsNode::put);
        RestAssured.given().contentType(ContentType.JSON).body(body.toString())
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}", groupId, artifactId).then()
                .statusCode(204);
        RestAssured.given().contentType(ContentType.JSON).body(body.toString())
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/1", groupId, artifactId).then()
                .statusCode(204);
    }

    /** {@code /.well-known/agents}, at the server root. */
    static RequestSpecification discovery() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        return RestAssured.given().baseUri("http://localhost:" + port).basePath("");
    }
}
