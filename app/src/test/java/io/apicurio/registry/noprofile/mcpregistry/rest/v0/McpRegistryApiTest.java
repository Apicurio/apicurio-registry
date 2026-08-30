package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for the official MCP Registry API.
 */
@QuarkusTest
@TestProfile(McpRegistryExperimentalFeaturesProfile.class)
public class McpRegistryApiTest extends AbstractResourceTestBase {

    private static final String BASE = "/mcp-registry/v0.1";
    private static final String REGISTRY_META = "io.modelcontextprotocol.registry/official";

    private String uniqueNamespace() {
        return "io.github.test" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String serverJson(String name, String version, String description) {
        return """
                {
                  "name": "%s",
                  "version": "%s",
                  "description": "%s",
                  "repository": {
                    "url": "https://github.com/example/weather",
                    "source": "github"
                  },
                  "packages": [
                    {
                      "registryType": "npm",
                      "identifier": "@example/weather-mcp",
                      "version": "%s",
                      "transport": { "type": "stdio" }
                    }
                  ]
                }
                """.formatted(name, version, description, version);
    }

    private void publish(String name, String version, String description) {
        given()
                .when()
                .contentType(CT_JSON)
                .body(serverJson(name, version, description))
                .post(BASE + "/publish")
                .then()
                .statusCode(200)
                .body("name", equalTo(name))
                .body("version", equalTo(version));
    }

    // === Publish and read back ===

    @Test
    public void testPublishAndGetServer() {
        String namespace = uniqueNamespace();
        String name = namespace + "/weather";

        publish(name, "1.0.0", "A weather server");

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/weather")
                .then()
                .statusCode(200)
                .body("name", equalTo(name))
                .body("version", equalTo("1.0.0"))
                .body("description", equalTo("A weather server"))
                .body("repository.url", equalTo("https://github.com/example/weather"))
                .body("packages", hasSize(1))
                .body("packages[0].registryType", equalTo("npm"))
                .body("packages[0].transport.type", equalTo("stdio"))
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("active"))
                .body("_meta.'" + REGISTRY_META + "'.isLatest", equalTo(true))
                .body("_meta.'" + REGISTRY_META + "'.id", notNullValue())
                .body("_meta.'" + REGISTRY_META + "'.publishedAt", notNullValue());
    }

    @Test
    public void testPublisherMetaIsPreservedAndRegistryMetaIsRecomputed() {
        String namespace = uniqueNamespace();
        String name = namespace + "/annotated";

        // The publisher's own '_meta' entry must survive, while the registry-managed block that the
        // publisher tried to set must be replaced by the registry's own view.
        String body = """
                {
                  "name": "%s",
                  "version": "1.0.0",
                  "_meta": {
                    "com.example/build": { "commit": "abc123" },
                    "%s": { "status": "deleted", "id": "spoofed" }
                  }
                }
                """.formatted(name, REGISTRY_META);

        given()
                .when()
                .contentType(CT_JSON)
                .body(body)
                .post(BASE + "/publish")
                .then()
                .statusCode(200)
                .body("_meta.'com.example/build'.commit", equalTo("abc123"))
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("active"));

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/annotated")
                .then()
                .statusCode(200)
                .body("_meta.'com.example/build'.commit", equalTo("abc123"))
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("active"))
                .body("_meta.'" + REGISTRY_META + "'.id", notNullValue())
                .body("_meta.'" + REGISTRY_META + "'.id", not(equalTo("spoofed")));
    }

    @Test
    public void testServerWithoutPackagesOmitsEmptyArrays() {
        String namespace = uniqueNamespace();
        String name = namespace + "/remoteonly";

        given()
                .when()
                .contentType(CT_JSON)
                .body("""
                        {
                          "name": "%s",
                          "version": "1.0.0",
                          "remotes": [ { "type": "streamable-http", "url": "https://example.com/mcp" } ]
                        }
                        """.formatted(name))
                .post(BASE + "/publish")
                .then()
                .statusCode(200);

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/remoteonly")
                .then()
                .statusCode(200)
                .body("remotes", hasSize(1))
                .body("packages", nullValue())
                .body("icons", nullValue());
    }

    @Test
    public void testRepublishingTheSameVersionConflicts() {
        String namespace = uniqueNamespace();
        String name = namespace + "/conflict";

        publish(name, "1.0.0", "First");

        given()
                .when()
                .contentType(CT_JSON)
                .body(serverJson(name, "1.0.0", "Second"))
                .post(BASE + "/publish")
                .then()
                .statusCode(409);
    }

    // === Versions ===

    @Test
    public void testListVersionsAndLatestTracking() {
        String namespace = uniqueNamespace();
        String name = namespace + "/multi";

        publish(name, "1.0.0", "v1");
        publish(name, "2.0.0", "v2");

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/multi/versions")
                .then()
                .statusCode(200)
                .body("servers", hasSize(2))
                .body("metadata.count", equalTo(2))
                .body("servers[0].version", equalTo("1.0.0"))
                .body("servers[1].version", equalTo("2.0.0"))
                .body("servers[0]._meta.'" + REGISTRY_META + "'.isLatest", equalTo(false))
                .body("servers[1]._meta.'" + REGISTRY_META + "'.isLatest", equalTo(true));

        // The bare server endpoint resolves to the most recently published version
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/multi")
                .then()
                .statusCode(200)
                .body("version", equalTo("2.0.0"));

        // ... and an explicit version returns exactly that one
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/multi/versions/1.0.0")
                .then()
                .statusCode(200)
                .body("version", equalTo("1.0.0"))
                .body("description", equalTo("v1"));
    }

    @Test
    public void testDeleteServerVersion() {
        String namespace = uniqueNamespace();
        String name = namespace + "/deletable";

        publish(name, "1.0.0", "v1");
        publish(name, "2.0.0", "v2");

        given()
                .when()
                .delete(BASE + "/servers/" + namespace + "/deletable/versions/2.0.0")
                .then()
                .statusCode(204);

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/deletable/versions/2.0.0")
                .then()
                .statusCode(404);

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/deletable")
                .then()
                .statusCode(200)
                .body("version", equalTo("1.0.0"));
    }

    // === Status ===

    @Test
    public void testUpdateSingleVersionStatus() {
        String namespace = uniqueNamespace();
        String name = namespace + "/statused";

        publish(name, "1.0.0", "v1");

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deprecated\"}")
                .patch(BASE + "/servers/" + namespace + "/statused/versions/1.0.0/status")
                .then()
                .statusCode(200)
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deprecated"));

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/statused/versions/1.0.0")
                .then()
                .statusCode(200)
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deprecated"));
    }

    @Test
    public void testDeletedStatusHidesVersionFromLatest() {
        String namespace = uniqueNamespace();
        String name = namespace + "/softdeleted";

        publish(name, "1.0.0", "v1");
        publish(name, "2.0.0", "v2");

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deleted\"}")
                .patch(BASE + "/servers/" + namespace + "/softdeleted/versions/2.0.0/status")
                .then()
                .statusCode(200)
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deleted"));

        // The soft-deleted version is skipped when resolving 'latest' ...
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/softdeleted")
                .then()
                .statusCode(200)
                .body("version", equalTo("1.0.0"));

        // ... but is still addressable directly, reporting its status.
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/softdeleted/versions/2.0.0")
                .then()
                .statusCode(200)
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deleted"));
    }

    @Test
    public void testUpdateStatusOfEveryVersion() {
        String namespace = uniqueNamespace();
        String name = namespace + "/allversions";

        publish(name, "1.0.0", "v1");
        publish(name, "2.0.0", "v2");
        publish(name, "3.0.0", "v3");

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deprecated\"}")
                .patch(BASE + "/servers/" + namespace + "/allversions/status")
                .then()
                .statusCode(200);

        for (String version : new String[] {"1.0.0", "2.0.0", "3.0.0"}) {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .get(BASE + "/servers/" + namespace + "/allversions/versions/" + version)
                    .then()
                    .statusCode(200)
                    .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deprecated"));
        }
    }

    @Test
    public void testAllVersionsStatusUpdateReachesSoftDeletedVersions() {
        String namespace = uniqueNamespace();
        String name = namespace + "/restorable";

        publish(name, "1.0.0", "v1");
        publish(name, "2.0.0", "v2");

        // Soft-delete one version, then restore every version in one call. A soft-deleted version maps to
        // a DISABLED artifact version, which the default retrieval behaviour hides -- so this is the case
        // where the bulk update can silently skip the very version the caller is trying to restore.
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deleted\"}")
                .patch(BASE + "/servers/" + namespace + "/restorable/versions/2.0.0/status")
                .then()
                .statusCode(200);

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"active\"}")
                .patch(BASE + "/servers/" + namespace + "/restorable/status")
                .then()
                .statusCode(200);

        for (String version : new String[] {"1.0.0", "2.0.0"}) {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .get(BASE + "/servers/" + namespace + "/restorable/versions/" + version)
                    .then()
                    .statusCode(200)
                    .body("_meta.'" + REGISTRY_META + "'.status", equalTo("active"));
        }

        // ... and the restored version is eligible to be 'latest' again.
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/restorable")
                .then()
                .statusCode(200)
                .body("version", equalTo("2.0.0"));
    }

    @Test
    public void testDeletingEveryVersionSucceedsRatherThanReporting404() {
        String namespace = uniqueNamespace();
        String name = namespace + "/gone";

        publish(name, "1.0.0", "v1");
        publish(name, "2.0.0", "v2");

        // Once every version is deleted there is no active version left to resolve, so the response must
        // be built from a concrete version rather than by resolving 'latest' after the fact.
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deleted\"}")
                .patch(BASE + "/servers/" + namespace + "/gone/status")
                .then()
                .statusCode(200)
                .body("name", equalTo(name))
                .body("version", equalTo("2.0.0"))
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deleted"));

        for (String version : new String[] {"1.0.0", "2.0.0"}) {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .get(BASE + "/servers/" + namespace + "/gone/versions/" + version)
                    .then()
                    .statusCode(200)
                    .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deleted"));
        }

        // A fully deleted server has no active version, so the bare endpoint is a genuine 404 ...
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/gone")
                .then()
                .statusCode(404);

        // ... and it can still be brought back.
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"active\"}")
                .patch(BASE + "/servers/" + namespace + "/gone/status")
                .then()
                .statusCode(200);

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/gone")
                .then()
                .statusCode(200)
                .body("version", equalTo("2.0.0"));
    }

    @Test
    public void testStatusUpdateRequiresAStatusField() {
        String namespace = uniqueNamespace();
        String name = namespace + "/nostatus";

        publish(name, "1.0.0", "v1");

        given()
                .when()
                .contentType(CT_JSON)
                .body("{}")
                .patch(BASE + "/servers/" + namespace + "/nostatus/versions/1.0.0/status")
                .then()
                .statusCode(400);
    }

    @Test
    public void testStatusMessageIsAcceptedWithDeprecated() {
        String namespace = uniqueNamespace();
        String name = namespace + "/withreason";

        publish(name, "1.0.0", "v1");

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deprecated\",\"statusMessage\":\"superseded by 2.0.0\"}")
                .patch(BASE + "/servers/" + namespace + "/withreason/versions/1.0.0/status")
                .then()
                .statusCode(200)
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deprecated"));
    }

    @Test
    public void testStatusMessageIsRejectedWithActive() {
        String namespace = uniqueNamespace();
        String name = namespace + "/badreason";

        publish(name, "1.0.0", "v1");

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"active\",\"statusMessage\":\"should not be allowed\"}")
                .patch(BASE + "/servers/" + namespace + "/badreason/versions/1.0.0/status")
                .then()
                .statusCode(400);
    }

    // === Identity ===

    @Test
    public void testMetaIdIsAGeneratedUuidNotTheGlobalId() {
        String namespace = uniqueNamespace();
        String name = namespace + "/uuidcheck";

        publish(name, "1.0.0", "v1");

        String id = given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/uuidcheck")
                .then()
                .statusCode(200)
                .extract().path("_meta.'" + REGISTRY_META + "'.id");

        // A UUID, not a small sequential integer: two registries publishing independently must never
        // collide on this value, which a globalId-based id cannot guarantee across instances.
        assertNotNull(id);
        assertDoesNotThrow(() -> UUID.fromString(id), "'id' must be a UUID, was: " + id);
    }

    @Test
    public void testMetaIdIsStableAcrossReadsAndVersions() {
        String namespace = uniqueNamespace();
        String name = namespace + "/stableid";

        publish(name, "1.0.0", "v1");

        String firstRead = given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/stableid/versions/1.0.0")
                .then()
                .statusCode(200)
                .extract().path("_meta.'" + REGISTRY_META + "'.id");

        String secondRead = given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/stableid/versions/1.0.0")
                .then()
                .statusCode(200)
                .extract().path("_meta.'" + REGISTRY_META + "'.id");

        assertEquals(firstRead, secondRead, "the same version's id must not change between reads");

        publish(name, "2.0.0", "v2");

        String v1IdAfterV2Published = given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/stableid/versions/1.0.0")
                .then()
                .statusCode(200)
                .extract().path("_meta.'" + REGISTRY_META + "'.id");

        assertEquals(firstRead, v1IdAfterV2Published,
                "publishing a new version must not change an existing version's id");

        String v2Id = given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/stableid/versions/2.0.0")
                .then()
                .statusCode(200)
                .extract().path("_meta.'" + REGISTRY_META + "'.id");

        assertNotEquals(firstRead, v2Id, "each version must get its own distinct id");
    }

    // === Listing, search and pagination ===

    @Test
    public void testListAndSearchServers() {
        String namespace = uniqueNamespace();
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        publish(namespace + "/alpha" + marker, "1.0.0", "Alpha server");
        publish(namespace + "/beta" + marker, "1.0.0", "Beta server");

        given()
                .when()
                .contentType(CT_JSON)
                .queryParam("search", "alpha" + marker)
                .get(BASE + "/servers")
                .then()
                .statusCode(200)
                .body("servers", hasSize(1))
                .body("metadata.count", equalTo(1))
                .body("servers[0].name", equalTo(namespace + "/alpha" + marker));
    }

    @Test
    public void testCursorPagination() {
        String namespace = uniqueNamespace();
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        for (int i = 0; i < 3; i++) {
            publish(namespace + "/srv" + marker + i, "1.0.0", "Server " + i);
        }

        String cursor = given()
                .when()
                .contentType(CT_JSON)
                .queryParam("search", "srv" + marker)
                .queryParam("limit", 2)
                .get(BASE + "/servers")
                .then()
                .statusCode(200)
                .body("servers", hasSize(2))
                .body("metadata.nextCursor", notNullValue())
                .extract().path("metadata.nextCursor");

        given()
                .when()
                .contentType(CT_JSON)
                .queryParam("search", "srv" + marker)
                .queryParam("limit", 2)
                .queryParam("cursor", cursor)
                .get(BASE + "/servers")
                .then()
                .statusCode(200)
                .body("servers", hasSize(1))
                .body("metadata.nextCursor", nullValue());
    }

    @Test
    public void testCursorIssuedForDifferentFiltersIsRejected() {
        String namespace = uniqueNamespace();
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        for (int i = 0; i < 3; i++) {
            publish(namespace + "/paged" + marker + i, "1.0.0", "Server " + i);
        }

        String cursor = given()
                .when()
                .contentType(CT_JSON)
                .queryParam("search", "paged" + marker)
                .queryParam("limit", 1)
                .get(BASE + "/servers")
                .then()
                .statusCode(200)
                .extract().path("metadata.nextCursor");
        assertNotNull(cursor);

        // An offset means nothing against a different result set, so the cursor must not be honoured.
        given()
                .when()
                .contentType(CT_JSON)
                .queryParam("search", "somethingelse")
                .queryParam("cursor", cursor)
                .get(BASE + "/servers")
                .then()
                .statusCode(400);
    }

    @Test
    public void testMalformedCursorIsRejected() {
        given()
                .when()
                .contentType(CT_JSON)
                .queryParam("cursor", "not-a-real-cursor")
                .get(BASE + "/servers")
                .then()
                .statusCode(400);
    }

    // === Validation and error handling ===

    @Test
    public void testPublishRejectsNameWithoutNamespace() {
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"name\":\"weather\",\"version\":\"1.0.0\"}")
                .post(BASE + "/publish")
                .then()
                .statusCode(400);
    }

    @Test
    public void testPublishRejectsMissingVersion() {
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"name\":\"" + uniqueNamespace() + "/weather\"}")
                .post(BASE + "/publish")
                .then()
                .statusCode(400);
    }

    @Test
    public void testPublishRejectsLatestAsAVersion() {
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"name\":\"" + uniqueNamespace() + "/weather\",\"version\":\"latest\"}")
                .post(BASE + "/publish")
                .then()
                .statusCode(400);
    }

    @Test
    public void testPathTraversalInServerNameIsRejected() {
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/..%2F..%2Fetc/passwd")
                .then()
                .statusCode(400);
    }

    @Test
    public void testUnknownServerReturns404() {
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + uniqueNamespace() + "/nosuchserver")
                .then()
                .statusCode(404);
    }

    @Test
    public void testUnknownVersionReturns404() {
        String namespace = uniqueNamespace();
        publish(namespace + "/known", "1.0.0", "v1");

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/known/versions/9.9.9")
                .then()
                .statusCode(404);
    }

    @Test
    public void testLimitBoundsThePageSize() {
        String namespace = uniqueNamespace();
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        publish(namespace + "/lim" + marker + "a", "1.0.0", "v1");
        publish(namespace + "/lim" + marker + "b", "1.0.0", "v1");

        given()
                .when()
                .contentType(CT_JSON)
                .queryParam("search", "lim" + marker)
                .queryParam("limit", 1)
                .get(BASE + "/servers")
                .then()
                .statusCode(200)
                .body("servers", hasSize(1))
                .body("metadata.count", equalTo(1));
    }

    @Test
    public void testPublishedServerIsStoredAsAnMcpServerArtifact() {
        String namespace = uniqueNamespace();
        String name = namespace + "/typed";

        publish(name, "1.0.0", "v1");

        String artifactType = given()
                .when()
                .contentType(CT_JSON)
                .get("/registry/v3/groups/" + namespace + "/artifacts/typed")
                .then()
                .statusCode(200)
                .extract().path("artifactType");

        assertEquals("MCP_SERVER", artifactType);
        assertNotEquals("MCP_TOOL", artifactType);
    }
}
