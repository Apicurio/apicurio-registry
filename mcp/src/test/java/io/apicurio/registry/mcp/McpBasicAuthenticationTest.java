package io.apicurio.registry.mcp;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test that verifies MCP server authentication works correctly
 * with Basic authentication against Keycloak.
 * <p>
 * This test starts both Keycloak and Registry containers, configures the
 * MCP server to authenticate with Registry using Basic auth (client credentials),
 * and verifies that authenticated requests succeed.
 * <p>
 * This test is tagged as "integration" and requires Docker to be running.
 * Run with: mvn test -Dtest=McpBasicAuthenticationTest -Dgroups=integration
 */
@QuarkusTest
@TestProfile(McpBasicAuthTestProfile.class)
@Tag("integration")
public class McpBasicAuthenticationTest {

    private static final Logger log = LoggerFactory.getLogger(McpBasicAuthenticationTest.class);

    @Inject
    RegistryService registryService;

    @Inject
    Utils utils;

    @Test
    public void testBasicAuthenticationGetServerInfo() {
        // The RegistryService should be able to connect to the Registry
        // using Basic authentication configured via the test profile
        var serverInfo = registryService.getServerInfo();

        assertNotNull(serverInfo, "Server info should not be null");
        assertNotNull(serverInfo.getVersion(), "Server version should not be null");

        log.info("Successfully authenticated with Basic Auth. Server info: {}", utils.toPrettyJson(serverInfo));
    }

    @Test
    public void testBasicAuthenticationListGroups() {
        // Test listing groups - this requires authentication
        var groups = registryService.listGroups("asc", "groupId");

        assertNotNull(groups, "Groups list should not be null");

        log.info("Successfully listed {} groups with Basic authentication", groups.size());
    }

    @Test
    public void testBasicAuthenticationCreateAndListGroup() {
        // Test creating a group - this requires write permissions (admin role)
        String testGroupId = "mcp-basic-auth-test-group-" + System.currentTimeMillis();

        var createdGroup = registryService.createGroup(testGroupId, "Test group for MCP basic authentication", null);

        assertNotNull(createdGroup, "Created group should not be null");
        assertNotNull(createdGroup.getGroupId(), "Group ID should not be null");

        log.info("Successfully created group with Basic authentication: {}", createdGroup.getGroupId());

        // Verify we can retrieve the group metadata
        var groupMetadata = registryService.getGroupMetadata(testGroupId);
        assertNotNull(groupMetadata, "Group metadata should not be null");

        log.info("Successfully retrieved group metadata: {}", utils.toPrettyJson(groupMetadata));
    }
}
