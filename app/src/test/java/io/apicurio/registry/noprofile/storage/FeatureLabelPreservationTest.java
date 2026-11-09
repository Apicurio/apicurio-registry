package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Labels owned by a registry feature survive metadata updates that replace the whole label map
 * (issue #10030). Runs on H2; subclasses run it on PostgreSQL and KafkaSQL.
 */
@QuarkusTest
public class FeatureLabelPreservationTest extends AbstractResourceTestBase {

    protected static final String CONTRACT_STATUS = "contract.status";
    private static final String MCP_STATUS_MESSAGE = "apicurio.mcp-registry.status.message";
    private static final String VISIBILITY = "apicurio.agent.visibility";
    private static final String SCHEMA = "{\"type\":\"object\"}";

    protected String group;

    protected String newArtifact() throws Exception {
        group = "labels-" + UUID.randomUUID();
        String artifactId = "a-" + UUID.randomUUID();
        createArtifact(group, artifactId, ArtifactType.JSON, SCHEMA, ContentTypes.APPLICATION_JSON);
        return artifactId;
    }

    protected void putArtifactLabels(String artifactId, Map<String, String> labels) {
        given().contentType(ContentType.JSON).body(Map.of("labels", labels))
                .put("/registry/v3/groups/{g}/artifacts/{a}", group, artifactId).then().statusCode(204);
    }

    protected Map<String, String> artifactLabels(String artifactId) {
        return labelsAt("/registry/v3/groups/" + group + "/artifacts/" + artifactId);
    }

    /** The labels of the resource at the path. The API omits an empty label map. */
    protected static Map<String, String> labelsAt(String path) {
        JsonPath json = given().get(path).then().statusCode(200).extract().jsonPath();
        return json.get("labels") == null ? Map.of() : json.getMap("labels", String.class, String.class);
    }

    @Test
    void artifactUpdateKeepsAnOmittedFeatureLabel() throws Exception {
        String artifactId = newArtifact();
        putArtifactLabels(artifactId, Map.of(CONTRACT_STATUS, "STABLE", "team", "a"));

        putArtifactLabels(artifactId, Map.of("team", "b"));

        assertEquals(Map.of(CONTRACT_STATUS, "STABLE", "team", "b"), artifactLabels(artifactId));
    }

    @Test
    void artifactUpdateAppliesAFeatureLabelTheCallerSends() throws Exception {
        String artifactId = newArtifact();
        putArtifactLabels(artifactId, Map.of(CONTRACT_STATUS, "STABLE"));

        putArtifactLabels(artifactId, Map.of(CONTRACT_STATUS, "DEPRECATED"));

        assertEquals(Map.of(CONTRACT_STATUS, "DEPRECATED"), artifactLabels(artifactId));
    }

    /** Only keys under a declared prefix are protected: "contractor" is not under "contract.". */
    @Test
    void aUserKeyThatOnlyLooksLikeAFeatureKeyIsNotKept() throws Exception {
        String artifactId = newArtifact();
        putArtifactLabels(artifactId, Map.of("contractor", "acme", "mcp-server-version-id-note", "x"));

        putArtifactLabels(artifactId, Map.of());

        assertEquals(Map.of(), artifactLabels(artifactId));
    }

    /** An update that sends no labels leaves them alone, and keeps the fields it does send. */
    @Test
    void anUpdateWithoutLabelsKeepsLabelsAndAppliesTheOtherFields() throws Exception {
        String artifactId = newArtifact();
        putArtifactLabels(artifactId, Map.of(CONTRACT_STATUS, "STABLE", "team", "a"));

        given().contentType(ContentType.JSON).body(Map.of("name", "Renamed", "description", "New"))
                .put("/registry/v3/groups/{g}/artifacts/{a}", group, artifactId).then().statusCode(204);

        JsonPath json = given().get("/registry/v3/groups/{g}/artifacts/{a}", group, artifactId).then()
                .statusCode(200).extract().jsonPath();
        assertEquals("Renamed", json.getString("name"));
        assertEquals("New", json.getString("description"));
        assertEquals(Map.of(CONTRACT_STATUS, "STABLE", "team", "a"), artifactLabels(artifactId));
    }

    /** The name and description sent with labels are applied, not dropped by the merge. */
    @Test
    void anUpdateWithLabelsAppliesTheOtherFields() throws Exception {
        String artifactId = newArtifact();
        putArtifactLabels(artifactId, Map.of(CONTRACT_STATUS, "STABLE"));

        given().contentType(ContentType.JSON)
                .body(Map.of("name", "Renamed", "description", "New", "labels", Map.of("team", "b")))
                .put("/registry/v3/groups/{g}/artifacts/{a}", group, artifactId).then().statusCode(204);

        JsonPath json = given().get("/registry/v3/groups/{g}/artifacts/{a}", group, artifactId).then()
                .statusCode(200).extract().jsonPath();
        assertEquals("Renamed", json.getString("name"));
        assertEquals("New", json.getString("description"));
        assertEquals(Map.of(CONTRACT_STATUS, "STABLE", "team", "b"), artifactLabels(artifactId));
    }

    /** Agent visibility is a user-managed label, so omitting it still removes it. */
    @Test
    void artifactUpdateStillRemovesUserOwnedLabels() throws Exception {
        String artifactId = newArtifact();
        putArtifactLabels(artifactId, Map.of(VISIBILITY, "public", "team", "a"));

        putArtifactLabels(artifactId, Map.of());

        assertEquals(Map.of(), artifactLabels(artifactId));
    }

    @Test
    void versionUpdateKeepsAnOmittedFeatureLabel() throws Exception {
        String artifactId = newArtifact();
        String path = "/registry/v3/groups/{g}/artifacts/{a}/versions/1";
        given().contentType(ContentType.JSON).body(Map.of("labels", Map.of(MCP_STATUS_MESSAGE, "moved", "x", "1")))
                .put(path, group, artifactId).then().statusCode(204);

        given().contentType(ContentType.JSON).body(Map.of("labels", Map.of("x", "2")))
                .put(path, group, artifactId).then().statusCode(204);

        assertEquals(Map.of(MCP_STATUS_MESSAGE, "moved", "x", "2"),
                labelsAt("/registry/v3/groups/" + group + "/artifacts/" + artifactId + "/versions/1"));

        given().contentType(ContentType.JSON).body(Map.of("labels", Map.of(MCP_STATUS_MESSAGE, "back")))
                .put(path, group, artifactId).then().statusCode(204);

        assertEquals(Map.of(MCP_STATUS_MESSAGE, "back"),
                labelsAt("/registry/v3/groups/" + group + "/artifacts/" + artifactId + "/versions/1"));
    }

    @Test
    void groupUpdateKeepsAnOmittedFeatureLabel() throws Exception {
        newArtifact();
        String path = "/registry/v3/groups/{g}";
        given().contentType(ContentType.JSON).body(Map.of("labels", Map.of(CONTRACT_STATUS, "STABLE", "x", "1")))
                .put(path, group).then().statusCode(204);

        given().contentType(ContentType.JSON).body(Map.of("labels", Map.of("x", "2")))
                .put(path, group).then().statusCode(204);

        assertEquals(Map.of(CONTRACT_STATUS, "STABLE", "x", "2"), labelsAt("/registry/v3/groups/" + group));

        given().contentType(ContentType.JSON).body(Map.of("labels", Map.of(CONTRACT_STATUS, "RETIRED")))
                .put(path, group).then().statusCode(204);

        assertEquals(Map.of(CONTRACT_STATUS, "RETIRED"), labelsAt("/registry/v3/groups/" + group));
    }

    /** The v2 metadata update always sends a full label map, built from its labels and properties. */
    @Test
    void v2MetadataUpdateKeepsTheArtifactsFeatureLabels() throws Exception {
        String artifactId = newArtifact();
        putArtifactLabels(artifactId, Map.of(CONTRACT_STATUS, "STABLE", "team", "a"));

        given().contentType(ContentType.JSON).body(Map.of("name", "renamed", "properties", Map.of("team", "b")))
                .put("/registry/v2/groups/{g}/artifacts/{a}/meta", group, artifactId).then().statusCode(204);

        assertEquals(Map.of(CONTRACT_STATUS, "STABLE", "team", "b"), artifactLabels(artifactId));
    }
}
