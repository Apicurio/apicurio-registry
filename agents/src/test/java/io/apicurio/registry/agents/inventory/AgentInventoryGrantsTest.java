package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Per-resource grants can deny creating an artifact name. A sync listing an allowed new agent and
 * a denied new one must be refused before either is written, not half done.
 */
@QuarkusTest
@TestProfile(AgentInventoryGrantsTest.GrantsProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AgentInventoryGrantsTest extends AbstractResourceTestBase {

    private static final String LOCATION = "projects/grants-project/locations/europe-west1";
    private static final String GROUP = "inventory-grants";

    static final Path GRANTS = createGrantsFile();

    private static Path createGrantsFile() {
        try {
            Path file = Files.createTempFile("inventory-grants", ".acl");
            Files.writeString(file, """
                    from io.apicurio.authz import Artifact, Group;
                    deny User with name = "bob" to WRITE Artifact with name like "%1$s/gcp-agent.grants-project.europe-west1.denied*";
                    allow User with name = "bob" to WRITE Group with name = "%1$s";
                    allow User with name = "bob" to WRITE Artifact with name like "%1$s/*";
                    otherwise deny;
                    """.formatted(GROUP));
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class GrantsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> map = new HashMap<>(AgentInventoryTestProfile.inventoryOverrides());
            map.put("quarkus.oidc.tenant-enabled", "false");
            map.put("quarkus.http.auth.basic", "true");
            map.put("apicurio.auth.role-based-authorization", "true");
            map.put("quarkus.security.users.embedded.enabled", "true");
            map.put("quarkus.security.users.embedded.plain-text", "true");
            map.put("quarkus.security.users.embedded.users.alice", "alice");
            map.put("quarkus.security.users.embedded.users.bob", "bob");
            map.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
            map.put("quarkus.security.users.embedded.roles.bob", "sr-developer");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.rules.path", GRANTS.toString());
            map.put("apicurio.auth.resource-based-authorization.rules.reload-enabled", "false");
            return map;
        }
    }

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(boundedClientOptions(registryV3ApiUrl, vertx).basicAuth("alice", "alice"));
    }

    private static String agent(String name) {
        return "{\"name\":\"" + LOCATION + "/agents/" + name + "\",\"displayName\":\"" + name + "\","
                + "\"protocols\":[{\"type\":\"A2A_AGENT\",\"protocolVersion\":\"1.0\",\"interfaces\":[{\"url\":"
                + "\"https://" + name + ".example.com/a2a\",\"protocolBinding\":\"JSONRPC\"}]}],"
                + "\"skills\":[{\"id\":\"s\",\"name\":\"S\",\"description\":\"S\",\"tags\":[\"t\"]}]}";
    }

    @Test
    void aDeniedNewArtifactRefusesTheWholeSync() {
        GoogleAgentRegistryStub.get().reset(LOCATION);
        // Listed first, so a check made only while writing would already have created it
        GoogleAgentRegistryStub.get().pages(LOCATION, "{\"agents\":[" + agent("allowed") + "," + agent("denied") + "]}");

        given().auth().preemptive().basic("bob", "bob").contentType(CT_JSON)
                .body("{\"source\":\"GOOGLE_AGENT_REGISTRY\",\"location\":\"" + LOCATION + "\"}")
                .post("/registry/v3/groups/" + GROUP + "/agent-inventory-sync").then().statusCode(403);

        given().auth().preemptive().basic("alice", "alice")
                .get("/registry/v3/groups/" + GROUP + "/artifacts/gcp-agent.grants-project.europe-west1.allowed")
                .then().statusCode(404);
        given().auth().preemptive().basic("alice", "alice")
                .get("/registry/v3/groups/" + GROUP + "/artifacts/gcp-agent.grants-project.europe-west1.denied")
                .then().statusCode(404);
    }
}
