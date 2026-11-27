package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.MySqlEmbeddedTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

/**
 * Regression tests for exact artifact deny rules on MySQL, where {@code ascii_general_ci} treats
 * trailing spaces as equal (#11413).
 */
@QuarkusTest
@TestProfile(GrantsCollationMysqlTest.MysqlGrantsProfile.class)
public class GrantsCollationMysqlTest extends AbstractResourceTestBase {

    private static final String RULES = """
            from io.apicurio.authz import Artifact, Group;
            deny User with name = "bob" to * Artifact with name = "team-b/bar";
            deny User with name = "bob" to * Artifact with name = "team-a/foo";
            allow User with name = "bob" to READ Artifact with name like "*";
            otherwise deny;
            """;

    static final Path GRANTS = createGrantsFile();

    private static Path createGrantsFile() {
        try {
            Path file = Files.createTempFile("grants-collation-mysql", ".acl");
            Files.writeString(file, RULES);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class MysqlGrantsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
            map.put("apicurio.storage.sql.kind", "mysql");
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.users.bob", "bob");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            map.put("quarkus.security.users.embedded.roles.bob", "sr-readonly");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.rules.path", GRANTS.toString());
            map.put("apicurio.auth.resource-based-authorization.rules.reload-enabled", "false");
            return map;
        }

        @Override
        public List<TestResourceEntry> testResources() {
            return List.of(new TestResourceEntry(MySqlEmbeddedTestResource.class));
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(boundedClientOptions(registryV3ApiUrl, vertx).basicAuth("alice", "alice"));
    }

    private static RequestSpecification alice() {
        return given().auth().preemptive().basic("alice", "alice");
    }

    private static RequestSpecification bob() {
        return given().auth().preemptive().basic("bob", "bob");
    }

    @Test
    public void prefetchedTrailingSpaceReferenceStillEnforcesExactDeny() {
        String secret = "{\"type\":\"object\",\"properties\":{\"secret\":{\"type\":\"string\"}}}";
        String leaf = "{\"type\":\"object\",\"properties\":{\"ok\":{\"type\":\"string\"}}}";
        alice().contentType(CT_JSON).body(Map.of("artifactId", "bar", "artifactType", "JSON",
                "firstVersion", Map.of("version", "1", "content", Map.of("content", secret,
                        "contentType", CT_JSON)))).post("/registry/v3/groups/team-b/artifacts").then().statusCode(200);
        alice().contentType(CT_JSON).body(Map.of("artifactId", "leaf", "artifactType", "JSON",
                "firstVersion", Map.of("version", "1", "content", Map.of("content", leaf,
                        "contentType", CT_JSON)))).post("/registry/v3/groups/pub/artifacts").then().statusCode(200);
        // Two references so prefetchArtifacts runs; one uses a trailing space matching team-b/bar on MySQL.
        alice().contentType(CT_JSON).body(Map.of("artifactId", "root", "artifactType", "JSON",
                "firstVersion", Map.of("version", "1", "content", Map.of(
                        "content", "{\"type\":\"object\",\"properties\":{\"a\":{\"$ref\":\"bar.json\"},\"b\":{\"$ref\":\"leaf.json\"}}}",
                        "contentType", CT_JSON,
                        "references", List.of(
                                Map.of("groupId", "team-b", "artifactId", "bar ", "version", "1", "name", "bar.json"),
                                Map.of("groupId", "pub", "artifactId", "leaf", "version", "1", "name", "leaf.json"))))))
                .post("/registry/v3/groups/pub/artifacts").then().statusCode(200);

        bob().get("/registry/v3/groups/pub/artifacts/leaf/versions/1/content").then().statusCode(200);
        bob().get("/registry/v3/groups/team-b/artifacts/bar/versions/1/content").then().statusCode(403);
        bob().queryParam("references", "DEREFERENCE")
                .get("/registry/v3/groups/pub/artifacts/root/versions/1/content").then().statusCode(403);
        alice().queryParam("references", "DEREFERENCE")
                .get("/registry/v3/groups/pub/artifacts/root/versions/1/content").then().statusCode(200)
                .body(containsString("secret"));
    }

    @Test
    public void globalIdWithTrailingSpaceVersionSpellingStillEnforcesExactDeny() {
        String v1 = "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}}}";
        String v2 = "{\"type\":\"object\",\"properties\":{\"b\":{\"type\":\"string\"}}}";
        long globalId1 = alice().contentType(CT_JSON).body(Map.of("artifactId", "foo", "artifactType", "JSON",
                "firstVersion", Map.of("version", "1", "content", Map.of("content", v1, "contentType", CT_JSON))))
                .post("/registry/v3/groups/team-a/artifacts").then().statusCode(200)
                .extract().jsonPath().getLong("version.globalId");
        // On MySQL, "foo " matches artifact "foo", and the new versions row stores "foo ".
        long globalId2 = alice().contentType(CT_JSON).queryParam("ifExists", "CREATE_VERSION")
                .body(Map.of("artifactId", "foo ", "artifactType", "JSON",
                        "firstVersion", Map.of("version", "2", "content", Map.of("content", v2, "contentType", CT_JSON))))
                .post("/registry/v3/groups/team-a/artifacts").then().statusCode(200)
                .extract().jsonPath().getLong("version.globalId");

        alice().get("/registry/v3/ids/globalIds/" + globalId1).then().statusCode(200);
        alice().get("/registry/v3/ids/globalIds/" + globalId2).then().statusCode(200);
        bob().get("/registry/v3/ids/globalIds/" + globalId1).then().statusCode(403);
        bob().get("/registry/v3/ids/globalIds/" + globalId2).then().statusCode(403);
    }
}
