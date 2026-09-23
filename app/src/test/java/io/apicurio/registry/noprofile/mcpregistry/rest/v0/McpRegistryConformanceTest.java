package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.apicurio.registry.storage.impl.sql.jdb.RuntimeSqlException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.VersionState;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.noprofile.mcpregistry.rest.v0.McpRegistryRequests.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestProfile(McpRegistryConformanceTest.DeletionDisabledProfile.class)
class McpRegistryConformanceTest extends AbstractResourceTestBase {

    public static class DeletionDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.features.experimental.enabled", "true",
                    "apicurio.mcp-registry.enabled", "true",
                    "apicurio.rest.deletion.artifact-version.enabled", "false");
        }
    }

    private static final String BASE = "/mcp-registry/v0.1";
    private static final String META = "_meta.'io.modelcontextprotocol.registry/official'";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    HandleFactory handles;

    private String namespace() {
        return "io.github.conformance" + UUID.randomUUID().toString().replace("-", "");
    }

    private void publish(String name, String version) {
        given().contentType(CT_JSON).body(Map.of("name", name, "version", version, "description", "Test"))
                .post(BASE + "/publish").then().statusCode(200).body("server.version", equalTo(version));
    }

    @Test
    void defaultListIncludesAllVersionsWhileLatestIsExplicit() {
        String ns = namespace();
        publish(ns + "/server", "1.0.0");
        publish(ns + "/server", "2.0.0");
        given().queryParam("search", ns).get(BASE + "/servers").then().statusCode(200)
                .body("servers.server.version", equalTo(List.of("1.0.0", "2.0.0")))
                .body("metadata.count", equalTo(2));
        given().queryParam("search", ns).queryParam("version", "latest").get(BASE + "/servers")
                .then().statusCode(200).body("servers.server.version", equalTo(List.of("2.0.0")));
    }

    @Test
    void deleteRetainsHistoryWhenPermanentVersionDeletionIsDisabled() {
        String ns = namespace();
        publish(ns + "/server", "1.0.0");
        String nativePath = "/registry/v3/groups/" + ns + "/artifacts/server/versions/1.0.0";
        given().delete(nativePath).then().statusCode(405);
        String path = BASE + "/servers/" + ns + "/server/versions/1.0.0";
        given().delete(path).then().statusCode(200).body(META + ".status", equalTo("deleted"));
        given().get(path).then().statusCode(404);
        given().queryParam("include_deleted", true).get(path).then().statusCode(200)
                .body("server.version", equalTo("1.0.0")).body(META + ".status", equalTo("deleted"));
        assertEquals(VersionState.DISABLED, storage.getArtifactVersionState(ns, "server", "1.0.0"));
        given().contentType(CT_JSON).body(Map.of("status", "active"))
                .patch(path + "/status").then().statusCode(200);
        given().get(path).then().statusCode(200).body(META + ".status", equalTo("active"));
    }

    @Test
    void draftVersionsNeverAppearInMcpDiscovery() {
        String ns = namespace();
        given().contentType(CT_JSON).body(Map.of("artifactId", "server", "artifactType", "MCP_SERVER",
                "firstVersion", Map.of("version", "draft", "isDraft", true,
                        "content", Map.of("content", "{}", "contentType", CT_JSON))))
                .post("/registry/v3/groups/" + ns + "/artifacts").then().statusCode(200);
        for (boolean includeDeleted : List.of(false, true)) {
            given().queryParam("search", ns).queryParam("include_deleted", includeDeleted)
                    .get(BASE + "/servers").then().statusCode(200).body("servers", hasSize(0));
            given().queryParam("include_deleted", includeDeleted)
                    .get(BASE + "/servers/" + ns + "/server/versions").then().statusCode(200)
                    .body("servers", hasSize(0));
            given().queryParam("include_deleted", includeDeleted)
                    .get(BASE + "/servers/" + ns + "/server/versions/draft").then().statusCode(404);
        }
    }

    @Test
    void allVersionPaginationAndDeletedLatestMetadataAgree() {
        String ns = namespace();
        publish(ns + "/server", "1.0.0");
        publish(ns + "/server", "2.0.0");
        String path = BASE + "/servers/" + ns + "/server";
        given().contentType(CT_JSON).body(Map.of("status", "deleted"))
                .patch(path + "/versions/2.0.0/status").then().statusCode(200);
        String cursor = given().queryParam("search", ns).queryParam("include_deleted", true)
                .queryParam("limit", 1).get(BASE + "/servers").then().statusCode(200)
                .body("servers.server.version", equalTo(List.of("1.0.0")))
                .body("servers[0]." + META + ".isLatest", equalTo(false))
                .extract().path("metadata.nextCursor");
        given().queryParam("search", ns).queryParam("include_deleted", true)
                .queryParam("limit", 1).queryParam("cursor", cursor).get(BASE + "/servers").then().statusCode(200)
                .body("servers.server.version", equalTo(List.of("2.0.0")))
                .body("servers[0]." + META + ".isLatest", equalTo(true));
        given().queryParam("include_deleted", true).get(path + "/versions/2.0.0").then().statusCode(200)
                .body(META + ".isLatest", equalTo(true));
    }

    @Test
    void statusUpdatesAreAtomicAndMessagesRoundTrip() {
        String ns = namespace();
        publish(ns + "/server", "1.0.0");
        publish(ns + "/server", "2.0.0");
        storage.updateArtifactVersionMetaData(ns, "server", "1.0.0",
                EditableVersionMetaDataDto.builder().labels(Map.of("PublisherKey", "MixedCase".repeat(100))).build());
        assertThrows(VersionNotFoundException.class, () -> storage.updateArtifactVersionStates(ns, "server",
                List.of("1.0.0", "missing"), VersionState.DEPRECATED, "apicurio.mcp-registry.status.",
                Map.of("apicurio.mcp-registry.status.message", "Failed")));
        assertEquals(VersionState.ENABLED, storage.getArtifactVersionState(ns, "server", "1.0.0"));
        var firstBefore = storage.getArtifactVersionMetaData(ns, "server", "1.0.0");
        var secondBefore = storage.getArtifactVersionMetaData(ns, "server", "2.0.0");
        String constraint = "mcp_atomic_" + UUID.randomUUID().toString().replace("-", "");
        // Fail on the second version at the database, after the first version has been updated.
        handles.withHandleNoException(handle -> {
            handle.createUpdate("ALTER TABLE versions ADD CONSTRAINT " + constraint
                    + " CHECK (globalId <> " + secondBefore.getGlobalId() + " OR state <> 'DEPRECATED')").execute();
            return null;
        });
        try {
            assertThrows(RuntimeSqlException.class, () -> storage.updateArtifactVersionStates(ns, "server",
                    List.of("1.0.0", "2.0.0"), VersionState.DEPRECATED, "apicurio.mcp-registry.status.",
                    Map.of("apicurio.mcp-registry.status.message", "Must roll back")));
            assertEquals(firstBefore, storage.getArtifactVersionMetaData(ns, "server", "1.0.0"));
            assertEquals(secondBefore, storage.getArtifactVersionMetaData(ns, "server", "2.0.0"));
        } finally {
            handles.withHandleNoException(handle -> {
                handle.createUpdate("ALTER TABLE versions DROP CONSTRAINT " + constraint).execute();
                return null;
            });
        }
        String path = BASE + "/servers/" + ns + "/server";
        given().contentType(CT_JSON).body(Map.of("status", "deprecated", "statusMessage", "Upgrade"))
                .patch(path + "/status").then().statusCode(200)
                .body("updatedCount", equalTo(2)).body("servers", hasSize(2));
        for (String version : List.of("1.0.0", "2.0.0")) {
            given().get(path + "/versions/" + version).then().statusCode(200)
                    .body(META + ".status", equalTo("deprecated"))
                    .body(META + ".statusMessage", equalTo("Upgrade"));
        }
        assertEquals("MixedCase".repeat(100), storage.getArtifactVersionMetaData(ns, "server", "1.0.0")
                .getLabels().get("PublisherKey"));
    }

    @Test
    void configuredCompatibilityRejectsRemovedRemoteButAllowsVersionUpgrade() {
        String ns = namespace();
        Map<String, Object> initial = Map.of("name", ns + "/server", "version", "1.0.0", "description", "Test",
                "remotes", List.of(Map.of("type", "streamable-http", "url", "https://example.com/mcp")));
        given().contentType(CT_JSON).body(initial).post(BASE + "/publish").then().statusCode(200);
        given().contentType(CT_JSON).body(Map.of("ruleType", "COMPATIBILITY", "config", "BACKWARD"))
                .post("/registry/v3/groups/" + ns + "/artifacts/server/rules").then().statusCode(204);
        Map<String, Object> upgrade = new HashMap<>(initial);
        upgrade.put("version", "2.0.0");
        given().contentType(CT_JSON).body(upgrade).post(BASE + "/publish").then().statusCode(200);
        upgrade.put("version", "3.0.0");
        upgrade.remove("remotes");
        given().contentType(CT_JSON).body(upgrade).post(BASE + "/publish").then().statusCode(400);
        given().get(BASE + "/servers/" + ns + "/server/versions/3.0.0").then().statusCode(404);
    }

    @Test
    void unchangedStatusIsRejectedAndUnicodeMessageLengthUsesCharacters() {
        String ns = namespace();
        publish(ns + "/server", "1.0.0");
        String path = BASE + "/servers/" + ns + "/server/versions/1.0.0";
        given().contentType(CT_JSON).body(Map.of("status", "active"))
                .patch(path + "/status").then().statusCode(400);
        String message = "\ud83d\ude80".repeat(500);
        given().contentType(CT_JSON).body(Map.of("status", "active", "statusMessage", message))
                .patch(path + "/status").then().statusCode(200)
                .body(META + ".statusMessage", equalTo(message));
        given().get(path).then().statusCode(200).body(META + ".statusMessage", equalTo(message));
    }

    @Test
    void manifestValidationRejectsNestedConstraintsAndCoercion() {
        String name = namespace() + "/server";
        for (Object version : List.of(123, true)) {
            given().contentType(CT_JSON).body(Map.of("name", name, "version", version, "description", "Test"))
                    .post(BASE + "/publish").then().statusCode(400);
        }
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of("packages", List.of(Map.of("registryType", "npm", "identifier", "test"))),
                Map.of("repository", Map.of("url", "https://example.com")),
                Map.of("remotes", List.of(Map.of("type", "stdio", "url", "https://example.com"))),
                Map.of("icons", List.of(Map.of("src", "https://example.com/i.png", "sizes", List.of("bad")))))) {
            var document = new HashMap<String, Object>(invalid);
            document.putAll(Map.of("name", name, "version", "1.0.0", "description", "Test"));
            given().contentType(CT_JSON).body(document).post(BASE + "/publish").then().statusCode(400);
        }
        given().contentType(CT_JSON).body(Map.of("name", name, "version", "1.0.0"))
                .post(BASE + "/publish").then().statusCode(400);
        given().contentType(CT_JSON).body(Map.of("name", name, "version", "1.0.0", "description", "x".repeat(101)))
                .post(BASE + "/publish").then().statusCode(400);
        given().get(BASE + "/servers/" + name + "/versions").then().statusCode(404);
    }
}
