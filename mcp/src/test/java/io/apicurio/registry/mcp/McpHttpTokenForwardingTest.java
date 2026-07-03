package io.apicurio.registry.mcp;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for MCP HTTP transport with inbound OIDC and bearer token forwarding to Registry.
 */
@QuarkusTest
@TestProfile(McpHttpAuthTestProfile.class)
@Tag("integration")
public class McpHttpTokenForwardingTest {

    private static final Logger log = LoggerFactory.getLogger(McpHttpTokenForwardingTest.class);

    @Test
    void testUnauthenticatedRequestIsRejected() {
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
                .statusCode(401);

        log.info("Unauthenticated initialize correctly returned 401");
    }

    @Test
    void testAdminTokenCanCallGetServerInfo() {
        String token = KeycloakTokenHelper.getClientCredentialsToken(
                AuthTestContainersManager.sharedTokenEndpoint(),
                AuthTestContainersManager.ADMIN_CLIENT_ID,
                AuthTestContainersManager.ADMIN_CLIENT_SECRET);

        assertNotNull(token);

        String sessionId = McpHttpTestHelper.initializeSession(token);

        McpHttpTestHelper.callTool(token, sessionId, 1, "get_server_info", "{}")
                .statusCode(200)
                .body(containsString("version"));

        log.info("Admin token successfully called get_server_info via HTTP MCP");
    }

    @Test
    void testReadonlyTokenCannotCreateGroup() {
        String token = KeycloakTokenHelper.getClientCredentialsToken(
                AuthTestContainersManager.sharedTokenEndpoint(),
                AuthTestContainersManager.READONLY_CLIENT_ID,
                AuthTestContainersManager.READONLY_CLIENT_SECRET);

        assertNotNull(token);

        String sessionId = McpHttpTestHelper.initializeSession(token);
        String groupId = "mcp-http-readonly-test-" + System.currentTimeMillis();

        McpHttpTestHelper.callTool(token, sessionId, 2, "create_group",
                """
                        {
                          "groupId": "%s",
                          "description": "should fail"
                        }
                        """.formatted(groupId))
                .statusCode(200)
                .body(not(containsString("\"groupId\":\"" + groupId + "\"")));

        log.info("Readonly token correctly denied create_group via HTTP MCP");
    }
}
