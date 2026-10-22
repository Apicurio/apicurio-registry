package io.apicurio.registry.mcp;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Helper for exercising the MCP streamable HTTP transport in integration tests.
 */
final class McpHttpTestHelper {

    static final String MCP_SESSION_HEADER = "Mcp-Session-Id";
    static final String MCP_PROTOCOL_HEADER = "Mcp-Protocol-Version";
    static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    private McpHttpTestHelper() {
    }

    static String initializeSession(String bearerToken) {
        Response response = given()
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json, text/event-stream")
                .header(MCP_PROTOCOL_HEADER, MCP_PROTOCOL_VERSION)
                .contentType("application/json")
                .body("""
                        {
                          "jsonrpc": "2.0",
                          "id": 0,
                          "method": "initialize",
                          "params": {
                            "protocolVersion": "%s",
                            "capabilities": {},
                            "clientInfo": {
                              "name": "mcp-http-test",
                              "version": "1.0"
                            }
                          }
                        }
                        """.formatted(MCP_PROTOCOL_VERSION))
                .post("/mcp")
                .then()
                .statusCode(200)
                .extract()
                .response();

        String sessionId = response.getHeader(MCP_SESSION_HEADER);
        assertNotNull(sessionId, "Mcp-Session-Id header should be present after initialize");

        given()
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json, text/event-stream")
                .header(MCP_SESSION_HEADER, sessionId)
                .header(MCP_PROTOCOL_HEADER, MCP_PROTOCOL_VERSION)
                .contentType("application/json")
                .body("""
                        {
                          "jsonrpc": "2.0",
                          "method": "notifications/initialized"
                        }
                        """)
                .post("/mcp")
                .then()
                .statusCode(202);

        return sessionId;
    }

    static ValidatableResponse callTool(String bearerToken, String sessionId, int id,
            String toolName, String argumentsJson) {
        return given()
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json, text/event-stream")
                .header(MCP_SESSION_HEADER, sessionId)
                .header(MCP_PROTOCOL_HEADER, MCP_PROTOCOL_VERSION)
                .contentType("application/json")
                .body("""
                        {
                          "jsonrpc": "2.0",
                          "id": %d,
                          "method": "tools/call",
                          "params": {
                            "name": "%s",
                            "arguments": %s
                          }
                        }
                        """.formatted(id, toolName, argumentsJson))
                .post("/mcp")
                .then();
    }
}
