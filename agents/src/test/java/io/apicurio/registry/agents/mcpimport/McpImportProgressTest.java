package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A long import reports progress, so it is not taken for one whose instance stopped. Tool writes
 * are held by {@link McpImportGateHook} to make the import last past the cutoff.
 */
@QuarkusTest
@TestProfile(McpImportProgressTestProfile.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(McpImportTest.LOCK)
public class McpImportProgressTest extends AbstractResourceTestBase {

    private static final String SERVER = "srv";

    McpTestServer mcp;

    @Inject
    McpImportGateHook gateHook;

    @Inject
    McpToolImportService importService;

    @Inject
    McpImportConfig importConfig;

    String group;

    @BeforeAll
    void startMcp() throws Exception {
        mcp = new McpTestServer();
    }

    @AfterAll
    void stopMcp() {
        if (mcp != null) {
            mcp.close();
        }
    }

    @AfterEach
    void awaitBackgroundImports() {
        gateHook.reset();
        await().atMost(Duration.ofSeconds(30))
                .until(() -> importService.availablePermits() == importConfig.getMaxConcurrent());
    }

    private String path() {
        return registryV3ApiUrl + "/groups/" + group + "/artifacts/" + SERVER;
    }

    private Map<String, String> labels() {
        return given().get(path()).then().statusCode(200).extract().jsonPath().getMap("labels");
    }

    private ValidatableResponse importStatus() {
        return given().get(path() + "/mcp-import/status").then();
    }

    private static Duration since(Instant instant) {
        return Duration.between(instant, Instant.now());
    }

    @Test
    void longImportReportsProgressAndIsNotReportedAsStale() throws Exception {
        group = McpImportGateHook.GROUP_PREFIX + TestUtils.generateGroupId();
        createArtifact(group, SERVER, ArtifactType.MCP_SERVER,
                "{\"name\":\"io.github.test/srv\",\"description\":\"A server\",\"version\":\"1.0.0\"}",
                ContentTypes.APPLICATION_JSON);
        mcp.tools(McpTestServer.tool("a", "string"), McpTestServer.tool("b", "string"),
                McpTestServer.tool("c", "string"));
        CompletableFuture<Void> second = gateHook.hold(SERVER + ".b");
        CompletableFuture<Void> third = gateHook.hold(SERVER + ".c");
        // Progress every total-timeout / 2; stale after total-timeout + timeout without it
        Duration interval = Duration.ofSeconds(importConfig.getTotalTimeoutSeconds() / 2);
        Duration cutoff = Duration.ofSeconds(importConfig.getTotalTimeoutSeconds() + importConfig.getTimeoutSeconds());

        given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("url", mcp.url(), "async", true))
                .post(path() + "/mcp-import").then().statusCode(202);
        await().atMost(Duration.ofSeconds(10)).until(() -> gateHook.reached(SERVER + ".b"));
        Instant startedOn = Instant.parse(labels().get(McpImportLabels.STARTED_ON));
        assertEquals(startedOn, Instant.parse(labels().get(McpImportLabels.UPDATED_ON)));

        // Let the second tool through once a progress report is due: it is recorded after the tool.
        await().atMost(interval.plusSeconds(5)).until(() -> since(startedOn).compareTo(interval) > 0);
        second.complete(null);
        await().atMost(Duration.ofSeconds(10)).until(() -> gateHook.reached(SERVER + ".c"));
        Instant updatedOn = Instant.parse(labels().get(McpImportLabels.UPDATED_ON));
        assertTrue(Duration.between(startedOn, updatedOn).compareTo(interval) > 0,
                () -> "updated-on " + updatedOn + " did not advance past " + startedOn.plus(interval));

        // Past the cutoff counted from the start, the import is still running, not stale.
        await().atMost(cutoff.plusSeconds(5)).until(() -> since(startedOn).compareTo(cutoff.plusMillis(500)) > 0);
        importStatus().statusCode(200).body("state", equalTo("RUNNING")).body("reason", nullValue());

        third.complete(null);
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> importStatus().statusCode(200).body("state", equalTo("COMPLETED")));
        importStatus().statusCode(200).body("version", equalTo("2")).body("counts", equalTo(Map.of("CREATED", 3)));
    }
}
