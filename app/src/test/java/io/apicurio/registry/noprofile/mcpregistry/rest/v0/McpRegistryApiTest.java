package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
                .body("server.name", equalTo(name))
                .body("server.version", equalTo(version));
    }

    // === Publish and read back ===

    @Test
    public void testEncodedServerNameAndResponseEnvelope() {
        String namespace = uniqueNamespace();
        String name = namespace + "/encoded";
        publish(name, "1.0.0", "Encoded name");
        given().urlEncodingEnabled(false)
                .get(BASE + "/servers/" + namespace + "%2Fencoded/versions/1.0.0")
                .then().statusCode(200).body("server.name", equalTo(name))
                .body("server.version", equalTo("1.0.0"))
                .body("name", nullValue())
                .body("server._meta.'" + REGISTRY_META + "'", nullValue())
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("active"));
        given().contentType(CT_JSON).body("{\"status\":\"deleted\"}")
                .patch(BASE + "/servers/" + namespace + "/encoded/versions/1.0.0/status")
                .then().statusCode(200);
        given().urlEncodingEnabled(false)
                .get(BASE + "/servers/" + namespace + "%2fencoded?include_deleted=true")
                .then().statusCode(200).body("server.name", equalTo(name))
                .body("server.version", equalTo("1.0.0"))
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deleted"));
    }

    @Test
    public void testWebsiteUrlAndPublisherMetadataRoundTripInEnvelope() {
        String name = uniqueNamespace() + "/website";
        given().contentType(CT_JSON).body("""
                {"name":"%s", "version":"1.0.0", "description":"Website test",
                 "websiteUrl":"https://example.com/mcp",
                 "_meta":{"com.example/build":{"revision":"abc"}}}
                """.formatted(name)).post(BASE + "/publish").then().statusCode(200)
                .body("server.websiteUrl", equalTo("https://example.com/mcp"))
                .body("server._meta.'com.example/build'.revision", equalTo("abc"))
                .body("server._meta.'" + REGISTRY_META + "'", nullValue())
                .body("_meta.'com.example/build'", nullValue());
        given().queryParam("search", name).get(BASE + "/servers").then().statusCode(200)
                .body("servers[0].server.websiteUrl", equalTo("https://example.com/mcp"))
                .body("servers[0].server._meta.'com.example/build'.revision", equalTo("abc"));
    }

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
                .body("server.name", equalTo(name))
                .body("server.version", equalTo("1.0.0"))
                .body("server.description", equalTo("A weather server"))
                .body("server.repository.url", equalTo("https://github.com/example/weather"))
                .body("server.packages", hasSize(1))
                .body("server.packages[0].registryType", equalTo("npm"))
                .body("server.packages[0].transport.type", equalTo("stdio"))
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
                .body("server._meta.'com.example/build'.commit", equalTo("abc123"))
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("active"));

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/annotated")
                .then()
                .statusCode(200)
                .body("server._meta.'com.example/build'.commit", equalTo("abc123"))
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
                .body("server.remotes", hasSize(1))
                .body("server.packages", nullValue())
                .body("server.icons", nullValue());
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
                .body("servers[0].server.version", equalTo("2.0.0"))
                .body("servers[1].server.version", equalTo("1.0.0"))
                .body("servers[0]._meta.'" + REGISTRY_META + "'.isLatest", equalTo(true))
                .body("servers[1]._meta.'" + REGISTRY_META + "'.isLatest", equalTo(false));

        // The bare server endpoint resolves to the most recently published version
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/multi")
                .then()
                .statusCode(200)
                .body("server.version", equalTo("2.0.0"));

        // ... and an explicit version returns exactly that one
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/multi/versions/1.0.0")
                .then()
                .statusCode(200)
                .body("server.version", equalTo("1.0.0"))
                .body("server.description", equalTo("v1"));
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
                .body("server.version", equalTo("1.0.0"));
    }

    @Test
    public void testDeleteRejectsLatestAsAVersion() {
        String namespace = uniqueNamespace();
        String versions = BASE + "/servers/" + namespace + "/pinned/versions";

        publish(namespace + "/pinned", "1.0.0", "v1");
        publish(namespace + "/pinned", "2.0.0", "v2");

        // 'latest' is whatever was published most recently when the request runs, which need not be the
        // version the caller looked at, so a mutation must name the version exactly.
        given()
                .when()
                .delete(versions + "/latest")
                .then()
                .statusCode(400)
                .body("error", equalTo("A concrete version is required: 'latest' may name a different"
                        + " version by the time the change is applied"));

        // Nothing was deleted.
        given()
                .when()
                .contentType(CT_JSON)
                .get(versions)
                .then()
                .statusCode(200)
                .body("servers", hasSize(2));
    }

    @Test
    public void testVersionStatusUpdateRejectsLatestAsAVersion() {
        String namespace = uniqueNamespace();
        String versions = BASE + "/servers/" + namespace + "/pinned/versions";

        publish(namespace + "/pinned", "1.0.0", "v1");
        publish(namespace + "/pinned", "2.0.0", "v2");

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deprecated\"}")
                .patch(versions + "/latest/status")
                .then()
                .statusCode(400)
                .body("error", equalTo("A concrete version is required: 'latest' may name a different"
                        + " version by the time the change is applied"));

        // The version 'latest' would have resolved to is untouched.
        given()
                .when()
                .contentType(CT_JSON)
                .get(versions + "/2.0.0")
                .then()
                .statusCode(200)
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("active"));
    }

    // === Error responses ===
    // The spec's error body is {"error": "..."}. The v3 ProblemDetails shape, which every MCP error used to
    // fall through to, adds title, detail and status, and names the Java exception class.

    @Test
    public void testNotFoundUsesTheSpecErrorShape() {
        String namespace = uniqueNamespace();

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/missing")
                .then()
                .statusCode(404)
                .body("error", equalTo("No MCP server exists at the requested coordinates"))
                .body("name", nullValue())
                .body("title", nullValue())
                .body("detail", nullValue());
    }

    @Test
    public void testConflictUsesTheSpecErrorShape() {
        String name = uniqueNamespace() + "/twice";
        publish(name, "1.0.0", "v1");

        given()
                .when()
                .contentType(CT_JSON)
                .body(serverJson(name, "1.0.0", "v1"))
                .post(BASE + "/publish")
                .then()
                .statusCode(409)
                .body("error", equalTo("Version '1.0.0' of MCP server '" + name + "' already exists"))
                .body("name", nullValue());
    }

    @Test
    public void testMalformedBodyUsesTheSpecErrorShape() {
        // Body deserialization errors are mapped by JacksonJsonMappingExceptionMapper, which is chosen by
        // exception type and never reaches the path dispatch in RegistryExceptionMapper.
        String namespace = uniqueNamespace();
        publish(namespace + "/badstatus", "1.0.0", "v1");

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"archived\"}")
                .patch(BASE + "/servers/" + namespace + "/badstatus/versions/1.0.0/status")
                .then()
                .statusCode(400)
                .body("error", equalTo("Not able to deserialize data provided."))
                .body("name", nullValue());
    }

    @Test
    public void testUnsupportedMethodDoesNotLeakFrameworkDetail() {
        String namespace = uniqueNamespace();
        publish(namespace + "/noput", "1.0.0", "v1");

        // RESTEasy's own message for this starts with a diagnostic code; only the reason phrase is returned.
        given()
                .when()
                .contentType(CT_JSON)
                .body(serverJson(namespace + "/noput", "1.0.0", "v1"))
                .put(BASE + "/servers/" + namespace + "/noput/versions/1.0.0")
                .then()
                .statusCode(405)
                .header("Allow", notNullValue())
                .body("error", equalTo("Method Not Allowed"));
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
                .body("server.version", equalTo("1.0.0"));

        // ... but is still addressable directly, reporting its status.
        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/softdeleted/versions/2.0.0")
                .then()
                .statusCode(404);
        given().queryParam("include_deleted", true)
                .get(BASE + "/servers/" + namespace + "/softdeleted/versions/2.0.0")
                .then().statusCode(200)
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
                .body("server.version", equalTo("2.0.0"));
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
                .body("server.name", equalTo(name))
                .body("server.version", equalTo("2.0.0"))
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deleted"));

        for (String version : new String[] {"1.0.0", "2.0.0"}) {
            given()
                    .when()
                    .contentType(CT_JSON)
                    .queryParam("include_deleted", true)
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
                .body("server.version", equalTo("2.0.0"));
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
                .body("servers[0].server.name", equalTo(namespace + "/alpha" + marker));
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

    /**
     * Servers in different namespaces may share a server id, which ties on artifactId. Ordering by a
     * field that ties makes offset paging skip and repeat rows, so every server must appear exactly once.
     */
    @Test
    public void testCursorPaginationAcrossNamespacesSharingAServerId() {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String serverId = "srv" + marker;

        Set<String> published = new HashSet<>();
        for (String prefix : List.of("aaa", "bbb", "ccc")) {
            String name = "io.github." + prefix + marker + "/" + serverId;
            publish(name, "1.0.0", "Server " + prefix);
            published.add(name);
        }

        List<String> seen = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < published.size(); page++) {
            var request = given().when().contentType(CT_JSON).queryParam("search", serverId)
                    .queryParam("limit", 1);
            if (cursor != null) {
                request = request.queryParam("cursor", cursor);
            }
            var response = request.get(BASE + "/servers").then().statusCode(200)
                    .body("servers", hasSize(1)).extract();
            seen.add(response.path("servers[0].server.name"));
            cursor = response.path("metadata.nextCursor");
        }

        assertEquals(published.size(), seen.size());
        assertEquals(published, new HashSet<>(seen), "paging must not skip or repeat a server");
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

    /**
     * The content validator runs on every publish, not only where an operator configured a validity rule.
     * A malformed 'repository' survives bean deserialization, so nothing else on the path rejects it.
     */
    @Test
    public void testPublishRejectsRepositoryWithoutUrl() {
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"name\":\"" + uniqueNamespace() + "/weather\",\"version\":\"1.0.0\","
                        + "\"repository\":{\"source\":\"github\"}}")
                .post(BASE + "/publish")
                .then()
                .statusCode(400);
    }

    @Test
    public void testPublishRejectsRemoteWithNonHttpUrl() {
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"name\":\"" + uniqueNamespace() + "/weather\",\"version\":\"1.0.0\","
                        + "\"remotes\":[{\"type\":\"streamable-http\",\"url\":\"not-a-url\"}]}")
                .post(BASE + "/publish")
                .then()
                .statusCode(400);
    }

    // === Paging and parameter edge cases ===

    @Test
    public void testListServersWithNoMatchesReturnsAnEmptyPage() {
        given()
                .when()
                .contentType(CT_JSON)
                .queryParam("search", "no-such-server-" + UUID.randomUUID())
                .get(BASE + "/servers")
                .then()
                .statusCode(200)
                .body("servers", hasSize(0))
                .body("metadata.count", equalTo(0))
                .body("metadata.nextCursor", nullValue());
    }

    @Test
    public void testCursorPaginationOnVersions() {
        String namespace = uniqueNamespace();
        String versions = BASE + "/servers/" + namespace + "/paged/versions";
        publish(namespace + "/paged", "1.0.0", "v1");
        publish(namespace + "/paged", "2.0.0", "v2");
        publish(namespace + "/paged", "3.0.0", "v3");

        String cursor = given()
                .when()
                .contentType(CT_JSON)
                .queryParam("limit", 2)
                .get(versions)
                .then()
                .statusCode(200)
                .body("servers.server.version", equalTo(List.of("3.0.0", "2.0.0")))
                .body("metadata.nextCursor", notNullValue())
                .extract().path("metadata.nextCursor");

        // The second page holds exactly the one version left: nothing skipped, nothing repeated.
        given()
                .when()
                .contentType(CT_JSON)
                .queryParam("limit", 2)
                .queryParam("cursor", cursor)
                .get(versions)
                .then()
                .statusCode(200)
                .body("servers.server.version", equalTo(List.of("1.0.0")))
                .body("metadata.nextCursor", nullValue());
    }

    @Test
    public void testVersionCursorIsRejectedOnAnotherServer() {
        String namespace = uniqueNamespace();
        publish(namespace + "/first", "1.0.0", "v1");
        publish(namespace + "/first", "2.0.0", "v2");
        publish(namespace + "/second", "1.0.0", "v1");

        String cursor = given()
                .when()
                .contentType(CT_JSON)
                .queryParam("limit", 1)
                .get(BASE + "/servers/" + namespace + "/first/versions")
                .then()
                .statusCode(200)
                .extract().path("metadata.nextCursor");

        // An offset into one server's versions means nothing for another server's.
        given()
                .when()
                .contentType(CT_JSON)
                .queryParam("cursor", cursor)
                .get(BASE + "/servers/" + namespace + "/second/versions")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }

    @Test
    public void testServerWideStatusRejectsAnUnknownStatusValue() {
        String namespace = uniqueNamespace();
        publish(namespace + "/archived", "1.0.0", "v1");

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"archived\"}")
                .patch(BASE + "/servers/" + namespace + "/archived/status")
                .then()
                .statusCode(400)
                .body("error", equalTo("Not able to deserialize data provided."));

        given()
                .when()
                .contentType(CT_JSON)
                .get(BASE + "/servers/" + namespace + "/archived/versions/1.0.0")
                .then()
                .statusCode(200)
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("active"));
    }

    @Test
    public void testLimitBelowOneIsRejected() {
        String namespace = uniqueNamespace();
        publish(namespace + "/limits", "1.0.0", "v1");
        List<String> paths = List.of(BASE + "/servers", BASE + "/servers/" + namespace + "/limits/versions");

        for (String path : paths) {
            for (String limit : List.of("0", "-1")) {
                given()
                        .when()
                        .contentType(CT_JSON)
                        .queryParam("limit", limit)
                        .get(path)
                        .then()
                        .statusCode(400)
                        .body("error", equalTo("'limit' must be at least 1"));
            }
        }
    }

    @Test
    public void testLimitBeyondTheIntRangeIsCappedNotTruncated() {
        // BigInteger.intValue() keeps only the low 32 bits, so 2^32 + 2 used to become a page size of 2.
        String namespace = uniqueNamespace();
        String versions = BASE + "/servers/" + namespace + "/huge/versions";
        publish(namespace + "/huge", "1.0.0", "v1");
        publish(namespace + "/huge", "2.0.0", "v2");
        publish(namespace + "/huge", "3.0.0", "v3");

        given()
                .when()
                .contentType(CT_JSON)
                .queryParam("limit", "4294967298")
                .get(versions)
                .then()
                .statusCode(200)
                .body("servers", hasSize(3))
                .body("metadata.nextCursor", nullValue());
    }

    @Test
    public void testStatusMessageOverTheSpecLimitIsRejectedForEveryStatus() {
        String namespace = uniqueNamespace();
        String server = BASE + "/servers/" + namespace + "/longreason";
        publish(namespace + "/longreason", "1.0.0", "v1");
        String tooLong = "x".repeat(501);

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deprecated\",\"statusMessage\":\"" + tooLong + "\"}")
                .patch(server + "/versions/1.0.0/status")
                .then()
                .statusCode(400)
                .body("error", equalTo("'statusMessage' must be at most 500 characters"));
        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deleted\",\"statusMessage\":\"" + tooLong + "\"}")
                .patch(server + "/status")
                .then()
                .statusCode(400)
                .body("error", equalTo("'statusMessage' must be at most 500 characters"));

        given()
                .when()
                .contentType(CT_JSON)
                .get(server + "/versions/1.0.0")
                .then()
                .statusCode(200)
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("active"));
    }

    @Test
    public void testStatusMessageAtTheSpecLimitIsAccepted() {
        String namespace = uniqueNamespace();
        publish(namespace + "/maxreason", "1.0.0", "v1");

        given()
                .when()
                .contentType(CT_JSON)
                .body("{\"status\":\"deprecated\",\"statusMessage\":\"" + "x".repeat(500) + "\"}")
                .patch(BASE + "/servers/" + namespace + "/maxreason/versions/1.0.0/status")
                .then()
                .statusCode(200)
                .body("_meta.'" + REGISTRY_META + "'.status", equalTo("deprecated"));
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
