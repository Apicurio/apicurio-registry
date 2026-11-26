package io.apicurio.registry.auth.grants;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Storage reads that per-resource authorization adds to a request (#10600). The administrator bypasses
 * the access rules, so the difference between the reads of {@code bob}, whose requests the rules
 * decide, and those of {@code alice} is what authorization costs.
 *
 * <p>Reads are counted as connection acquisitions. Background jobs can acquire connections at the
 * same time, so each count is the smallest of several runs of the same request.</p>
 */
@QuarkusTest
@TestProfile(GrantsStorageReadsTest.ReadsProfile.class)
public class GrantsStorageReadsTest extends AbstractResourceTestBase {

    private static final String GROUP = "storage-reads";
    private static final int RUNS = 5;

    private static final String RULES = """
            from io.apicurio.authz import Artifact, Group;
            allow User with name = "bob" to READ Artifact with name like "%1$s/*";
            otherwise deny;
            """.formatted(GROUP);

    static final Path GRANTS = createGrantsFile();

    private static Path createGrantsFile() {
        try {
            Path file = Files.createTempFile("grants-storage-reads", ".acl");
            Files.writeString(file, RULES);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class ReadsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>();
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
            // Content is otherwise cached across requests, which would hide the endpoint's own reads
            map.put("apicurio.storage.content-cache.enabled", "false");
            return map;
        }
    }

    @Inject
    @Named("application")
    AgroalDataSource dataSource;

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

    private static void create(String artifactId, String content, List<Map<String, Object>> references) {
        alice().contentType(CT_JSON).body(Map.of("artifactId", artifactId, "artifactType", "JSON",
                "firstVersion", Map.of("version", "1", "content", Map.of("content", content,
                        "contentType", CT_JSON, "references", references))))
                .post("/registry/v3/groups/" + GROUP + "/artifacts").then().statusCode(200);
    }

    private static Map<String, Object> reference(String artifactId) {
        return Map.of("groupId", GROUP, "artifactId", artifactId, "version", "1", "name", artifactId + ".json");
    }

    /** The fewest connections a request acquires over several runs. */
    private long reads(Supplier<RequestSpecification> as, String path, Map<String, String> query) {
        long fewest = Long.MAX_VALUE;
        for (int i = 0; i < RUNS; i++) {
            long before = dataSource.getMetrics().acquireCount();
            as.get().queryParams(query).get(path).then().statusCode(200);
            fewest = Math.min(fewest, dataSource.getMetrics().acquireCount() - before);
        }
        return fewest;
    }

    @Test
    public void authorizationAddsNoReadToAGlobalIdAndOneReadPerReferenceLevel() {
        String leaf = "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}}}";
        create("leaf-1", leaf, List.of());
        create("leaf-2", leaf, List.of());
        create("leaf-3", leaf, List.of());
        create("root", "{\"type\":\"object\",\"properties\":{"
                        + "\"a\":{\"$ref\":\"leaf-1.json\"},\"b\":{\"$ref\":\"leaf-2.json\"},"
                        + "\"c\":{\"$ref\":\"leaf-3.json\"}}}",
                List.of(reference("leaf-1"), reference("leaf-2"), reference("leaf-3")));
        long globalId = alice().get("/registry/v3/groups/" + GROUP + "/artifacts/root/versions/1").then()
                .statusCode(200).extract().jsonPath().getLong("globalId");

        String byGlobalId = "/registry/v3/ids/globalIds/" + globalId;
        assertEquals(reads(GrantsStorageReadsTest::alice, byGlobalId, Map.of()),
                reads(GrantsStorageReadsTest::bob, byGlobalId, Map.of()),
                "reads added by authorizing a global ID");

        String content = "/registry/v3/groups/" + GROUP + "/artifacts/root/versions/1/content";
        assertEquals(1, reads(GrantsStorageReadsTest::bob, content, Map.of())
                        - reads(GrantsStorageReadsTest::alice, content, Map.of()),
                "reads added by authorizing an artifact");

        // The root, then its three references together
        Map<String, String> dereference = Map.of("references", "DEREFERENCE");
        assertEquals(2, reads(GrantsStorageReadsTest::bob, content, dereference)
                        - reads(GrantsStorageReadsTest::alice, content, dereference),
                "reads added by authorizing a dereference with three references");
    }
}
