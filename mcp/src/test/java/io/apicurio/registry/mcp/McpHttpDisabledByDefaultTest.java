package io.apicurio.registry.mcp;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Verifies that MCP HTTP endpoints are not reachable unless HTTP mode is explicitly enabled.
 */
@QuarkusTest
public class McpHttpDisabledByDefaultTest {

    @Test
    void testMcpEndpointNotReachableWhenHttpModeDisabled() {
        io.restassured.RestAssured.given()
                .header("Accept", "application/json, text/event-stream")
                .contentType("application/json")
                .body("""
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "method": "initialize",
                          "params": {
                            "protocolVersion": "2024-11-05",
                            "capabilities": {},
                            "clientInfo": {
                              "name": "mcp-http-test",
                              "version": "1.0"
                            }
                          }
                        }
                        """)
                .post("/mcp")
                .then()
                .statusCode(503)
                .body(org.hamcrest.Matchers.containsString("MCP HTTP transport is disabled"));
    }
}
