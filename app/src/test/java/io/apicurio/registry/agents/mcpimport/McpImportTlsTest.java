package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.agents.mcpimport.McpImportException.Reason;
import io.apicurio.registry.agents.mcpimport.McpToolsClient.ServerTools;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.http.HttpServerOptions;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An HTTPS MCP server whose certificate only the named TLS configuration trusts.
 */
@QuarkusTest
@TestProfile(McpImportTlsTestProfile.class)
public class McpImportTlsTest {

    @Inject
    McpToolsClient toolsClient;

    McpTestServer server;

    @BeforeEach
    void start() throws Exception {
        server = new McpTestServer(new HttpServerOptions().setSsl(true)
                .setKeyCertOptions(McpImportTlsTestProfile.CERTIFICATE.keyCertOptions()), "localhost");
        server.tools(McpTestServer.tool("secure", "string"));
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void namedTlsConfigurationIsTrusted() {
        ServerTools tools = toolsClient.listTools(URI.create(server.url()), Map.of());

        assertEquals("test-server", tools.serverInfo().name());
        assertEquals(List.of("secure"), tools.tools().stream().map(t -> t.name()).toList());
    }

    @Test
    void jvmDefaultTruststoreDoesNotTrustTheServer() {
        McpImportException e = assertThrows(McpImportException.class, () -> new McpToolsClient()
                .listTools(URI.create(server.url()), Map.of(), new McpToolsClient.Limits(Duration.ofSeconds(5),
                        Duration.ofSeconds(30), 65536, 10, Long.MAX_VALUE)));

        assertEquals(Reason.PROTOCOL, e.getReason());
    }
}
