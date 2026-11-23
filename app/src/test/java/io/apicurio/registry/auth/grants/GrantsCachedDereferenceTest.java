package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
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

/**
 * With HTTP caching enabled, a conditional request for dereferenced content must check the references
 * before answering 304: a matching ETag must not skip the access check of the referenced artifacts.
 */
@QuarkusTest
@TestProfile(GrantsCachedDereferenceTest.CachingProfile.class)
public class GrantsCachedDereferenceTest extends AbstractResourceTestBase {

    private static final String GROUP = "cached-deref";

    private static final String RULES = """
            from io.apicurio.authz import Artifact, Group;
            allow User with name = "bob" to READ Artifact with name = "%1$s/root";
            otherwise deny;
            """.formatted(GROUP);

    static final Path GRANTS = createGrantsFile();

    private static Path createGrantsFile() {
        try {
            Path file = Files.createTempFile("grants-cached-deref", ".acl");
            Files.writeString(file, RULES);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class CachingProfile implements QuarkusTestProfile {
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
            map.put("apicurio.auth.resource-based-authorization.owner-bypass.enabled", "false");
            map.put("apicurio.http-caching.enabled", "true");
            return map;
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

    private static void create(String artifactId, String content, List<Map<String, Object>> references) {
        alice().contentType(CT_JSON).body(Map.of("artifactId", artifactId, "artifactType", "JSON",
                "firstVersion", Map.of("version", "1", "content", Map.of("content", content,
                        "contentType", CT_JSON, "references", references))))
                .post("/registry/v3/groups/" + GROUP + "/artifacts").then().statusCode(200);
    }

    @Test
    public void aConditionalDereferenceStillChecksTheReferences() {
        create("referenced", "{\"type\":\"object\",\"properties\":{\"secret\":{\"type\":\"string\"}}}", List.of());
        create("root", "{\"type\":\"object\",\"properties\":{\"r\":{\"$ref\":\"referenced.json\"}}}",
                List.of(Map.of("groupId", GROUP, "artifactId", "referenced", "version", "1",
                        "name", "referenced.json")));
        long globalId = alice().get("/registry/v3/groups/" + GROUP + "/artifacts/root/versions/1")
                .then().statusCode(200).extract().jsonPath().getLong("globalId");
        long contentId = alice().get("/registry/v3/groups/" + GROUP + "/artifacts/root/versions/1")
                .then().statusCode(200).extract().jsonPath().getLong("contentId");

        for (String path : List.of("/registry/v3/ids/globalIds/" + globalId,
                "/registry/v3/ids/contentIds/" + contentId,
                "/registry/v3/groups/" + GROUP + "/artifacts/root/versions/1/content")) {
            String eTag = alice().queryParam("references", "DEREFERENCE").get(path).then().statusCode(200)
                    .extract().header("ETag");
            alice().header("If-None-Match", eTag).queryParam("references", "DEREFERENCE").get(path).then()
                    .statusCode(304);

            bob().queryParam("references", "DEREFERENCE").get(path).then().statusCode(403);
            bob().header("If-None-Match", eTag).queryParam("references", "DEREFERENCE").get(path).then()
                    .statusCode(403);
            // bob may read the root itself, so its own content still validates
            String preserveTag = bob().get(path).then().statusCode(200).extract().header("ETag");
            bob().header("If-None-Match", preserveTag).get(path).then().statusCode(304);
        }
    }
}
