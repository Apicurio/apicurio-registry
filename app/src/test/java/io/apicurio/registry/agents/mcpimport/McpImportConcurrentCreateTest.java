package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.CreateArtifact;
import io.apicurio.registry.rest.v3.impl.GroupsResourceImpl;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * The import looks up existing tool artifacts once, before writing. An artifact another request
 * creates after that lookup was neither type-checked nor authorized, so the import must not add a
 * version to it.
 */
@QuarkusTest
@TestProfile(McpImportTestProfile.class)
@ResourceLock(McpImportTest.LOCK)
public class McpImportConcurrentCreateTest extends AbstractResourceTestBase {

    @InjectSpy
    GroupsResourceImpl groups;

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
                    .body("tools[1].causes[0].context", equalTo("/artifactId"));
        }

        assertTrue(interleaved.get(), "the second tool was created during the import");
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/srv.b").then().statusCode(200)
                .body("artifactType", equalTo(ArtifactType.JSON));
        given().get(registryV3ApiUrl + "/groups/" + group + "/artifacts/srv.b/versions").then().statusCode(200)
                .body("count", equalTo(1));
    }
}
