package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.CreateArtifact;
import io.apicurio.registry.rest.v3.impl.GroupsResourceImpl;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Concurrent creation during an MCP import: a non-tool artifact created after preflight is rejected,
 * an {@code MCP_TOOL} artifact created after preflight is authorized and reused, and simultaneous
 * starts on the same server allow only one import to run.
 */
@QuarkusTest
@TestProfile(McpImportTestProfile.class)
@ResourceLock(McpImportTest.LOCK)
public class McpImportConcurrentCreateTest extends AbstractResourceTestBase {

    @InjectSpy
    GroupsResourceImpl groups;

    @Inject
    McpImportStatusStore statusStore;

    @Test
    void toolArtifactCreatedDuringTheImportIsLeftAlone() throws Exception {
        String group = TestUtils.generateGroupId();
        createArtifact(group, "srv", ArtifactType.MCP_SERVER,
                "{\"name\":\"io.github.test/srv\",\"description\":\"A server\",\"version\":\"1.0.0\"}",
                ContentTypes.APPLICATION_JSON);
        AtomicBoolean interleaved = new AtomicBoolean();
        // While the first tool is created, another request creates the second tool's ID as JSON
        doAnswer(invocation -> {
            CreateArtifact create = invocation.getArgument(4);
            if ("srv.a".equals(create.getArtifactId()) && interleaved.compareAndSet(false, true)) {
                createArtifact(group, "srv.b", ArtifactType.JSON, "{\"type\":\"string\"}",
                        ContentTypes.APPLICATION_JSON);
            }
            return invocation.callRealMethod();
        }).when(groups).createArtifact(anyString(), any(), anyBoolean(), any(), any());

        try (McpTestServer mcp = new McpTestServer()) {
            mcp.tools(McpTestServer.tool("a", "string"), McpTestServer.tool("b", "string"));
            given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("url", mcp.url()))
                    .post(registryV3ApiUrl + "/groups/" + group + "/artifacts/srv/mcp-import").then()
                    .statusCode(200)
                    .body("tools[0].status", equalTo("CREATED"))
                    .body("tools[1].status", equalTo("REJECTED"))
                    .body("tools[1].causes[0].context", equalTo("/artifactType"));
        }

        assertTrue(interleaved.get(), "the second tool was created during the import");
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/srv.b").then().statusCode(200)
                .body("artifactType", equalTo(ArtifactType.JSON));
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/srv.b/versions").then().statusCode(200)
                .body("count", equalTo(1));
    }

    @Test
    void mcpToolArtifactCreatedDuringTheImportIsReusedAndReferenced() throws Exception {
        String group = TestUtils.generateGroupId();
        createArtifact(group, "srv", ArtifactType.MCP_SERVER,
                "{\"name\":\"io.github.test/srv\",\"description\":\"A server\",\"version\":\"1.0.0\"}",
                ContentTypes.APPLICATION_JSON);
        AtomicBoolean interleaved = new AtomicBoolean();
        // While the first tool is created, another request creates the second tool as an MCP_TOOL
        doAnswer(invocation -> {
            CreateArtifact create = invocation.getArgument(4);
            if ("srv.a".equals(create.getArtifactId()) && interleaved.compareAndSet(false, true)) {
                createArtifact(group, "srv.b", ArtifactType.MCP_TOOL,
                        McpTestServer.tool("b", "string").encode(),
                        ContentTypes.APPLICATION_JSON);
            }
            return invocation.callRealMethod();
        }).when(groups).createArtifact(anyString(), any(), anyBoolean(), any(), any());

        try (McpTestServer mcp = new McpTestServer()) {
            mcp.tools(McpTestServer.tool("a", "string"), McpTestServer.tool("b", "string"));
            given().contentType(ContentTypes.APPLICATION_JSON).body(Map.of("url", mcp.url()))
                    .post(registryV3ApiUrl + "/groups/" + group + "/artifacts/srv/mcp-import").then()
                    .statusCode(200)
                    .body("version", equalTo("2"))
                    .body("tools[0].status", equalTo("CREATED"))
                    .body("tools[1].status", equalTo("UNCHANGED"))
                    .body("tools[1].version", equalTo("1"));
        }

        assertTrue(interleaved.get(), "the second tool was created during the import");
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/srv/versions/2/references").then()
                .statusCode(200)
                .body("size()", equalTo(2));
    }

    @Test
    void simultaneousStartsOnSameServerAllowOnlyOne() throws Exception {
        String group = TestUtils.generateGroupId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                String server = "srv-" + round;
                createArtifact(group, server, ArtifactType.MCP_SERVER,
                        "{\"name\":\"io.github.test/" + server + "\",\"description\":\"A server\",\"version\":\"1.0.0\"}",
                        ContentTypes.APPLICATION_JSON);
                CyclicBarrier barrier = new CyclicBarrier(2);
                Callable<Boolean> starter = () -> {
                    ManagedContext requestContext = Arc.container().requestContext();
                    requestContext.activate();
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        try {
                            statusStore.started(group, server, "import-" + Thread.currentThread().threadId(),
                                    Instant.now());
                            return true;
                        } catch (McpImportException e) {
                            assertEquals(McpImportException.Reason.ALREADY_RUNNING, e.getReason());
                            return false;
                        }
                    } finally {
                        requestContext.terminate();
                    }
                };
                List<Future<Boolean>> futures = List.of(pool.submit(starter), pool.submit(starter));
                int started = (futures.get(0).get(15, TimeUnit.SECONDS) ? 1 : 0)
                        + (futures.get(1).get(15, TimeUnit.SECONDS) ? 1 : 0);
                assertEquals(1, started, "exactly one simultaneous start must succeed in round " + round);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
