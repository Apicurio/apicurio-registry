package io.apicurio.registry.auth.grants;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Hot reload of the grants file: a scheduled job picks up changes without a restart, and an
 * invalid edit keeps the previous grants.
 */
@QuarkusTest
@TestProfile(GrantsHotReloadTest.ReloadProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class GrantsHotReloadTest extends AbstractResourceTestBase {

    private static final String GROUP = "grants-reload";
    private static final String NO_GRANTS = """
            {"config": {"admin_roles": ["sr-admin"]}, "grants": []}""";
    private static final String BOB_READS = """
            {"config": {"admin_roles": ["sr-admin"]}, "grants": [
              {"principal": "bob", "operation": "read", "resource_type": "artifact",
               "resource_pattern_type": "prefix", "resource_pattern": "grants-reload/"}]}""";

    static final Path GRANTS = createGrantsFile();

    private static Path createGrantsFile() {
        try {
            Path file = Files.createTempFile("grants-reload", ".json");
            Files.writeString(file, NO_GRANTS);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class ReloadProfile implements QuarkusTestProfile {
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
            map.put("quarkus.security.users.embedded.roles.bob", "sr-developer");
            map.put("apicurio.features.experimental.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.enabled", "true");
            map.put("apicurio.auth.resource-based-authorization.grants.path", GRANTS.toString());
            map.put("apicurio.auth.resource-based-authorization.grants.reload-every", "1s");
            return map;
        }
    }

    @Inject
    Scheduler scheduler;

    @Override
    protected RegistryClient createRestClientV3(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(registryV3ApiUrl).vertx(vertx).basicAuth("alice", "alice"));
    }

    private static void write(String content, long modifiedOffsetMs) throws IOException {
        Files.writeString(GRANTS, content);
        // Distinct modification time even on filesystems with coarse timestamps
        Files.setLastModifiedTime(GRANTS, FileTime.fromMillis(System.currentTimeMillis() + modifiedOffsetMs));
    }

    private int bobStatus(String artifactId) {
        return given().auth().preemptive().basic("bob", "bob")
                .get("/registry/v3/groups/" + GROUP + "/artifacts/" + artifactId).statusCode();
    }

    @Test
    public void reloadAppliesChangesAndKeepsGrantsOnInvalidEdit() throws Exception {
        assertNotNull(scheduler.getScheduledJob(GrantsAccessControllerInitializer.RELOAD_JOB));
        String artifactId = TestUtils.generateArtifactId();
        clientV3.groups().byGroupId(GROUP).artifacts().post(TestUtils.clientCreateArtifact(artifactId,
                ArtifactType.JSON, "{\"type\":\"object\"}", ContentTypes.APPLICATION_JSON));
        assertEquals(403, bobStatus(artifactId));

        write(BOB_READS, 10_000);
        await().atMost(Duration.ofSeconds(15)).until(() -> bobStatus(artifactId) == 200);

        write("{ not json", 20_000);
        // Two reload intervals: the invalid file is seen and rejected, previous grants stay
        Thread.sleep(2_500);
        assertEquals(200, bobStatus(artifactId));
    }
}
