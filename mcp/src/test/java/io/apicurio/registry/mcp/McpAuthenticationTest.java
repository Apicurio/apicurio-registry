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
 * with OAuth2/OIDC authentication against Keycloak.
 * <p>
 * This test starts both Keycloak and Registry containers, configures the
 * MCP server to authenticate with Registry using OAuth2 client credentials,
 * and verifies that authenticated requests succeed.
 * <p>
 * This test is tagged as "integration" and requires Docker to be running.
 * Run with: mvn test -Dtest=McpAuthenticationTest -Dgroups=integration
 */
@QuarkusTest
@TestProfile(McpAuthTestProfile.class)
@Tag("integration")
public class McpAuthenticationTest {

    private static final Logger log = LoggerFactory.getLogger(McpAuthenticationTest.class);

    @Inject
    RegistryService registryService;

    @Inject
    Utils utils;

    @Test
    public void testOAuth2AuthenticationGetServerInfo() {
        // The RegistryService should be able to connect to the Registry
        // using OAuth2 authentication configured via the test profile
        var serverInfo = registryService.getServerInfo();

        assertNotNull(serverInfo, "Server info should not be null");
        assertNotNull(serverInfo.getVersion(), "Server version should not be null");

        log.info("Successfully authenticated with OAuth2. Server info: {}", utils.toPrettyJson(serverInfo));
    }

    @Test
    public void testOAuth2AuthenticationListGroups() {
        // Test listing groups - this requires authentication
        var groups = registryService.listGroups("asc", "groupId");

        assertNotNull(groups, "Groups list should not be null");

        log.info("Successfully listed {} groups with OAuth2 authentication", groups.size());
    }

    @Test
    public void testOAuth2AuthenticationCreateAndListGroup() {
        // Test creating a group - this requires write permissions (admin role)
        String testGroupId = "mcp-auth-test-group-" + System.currentTimeMillis();

        var createdGroup = registryService.createGroup(testGroupId, "Test group for MCP authentication", null);

        assertNotNull(createdGroup, "Created group should not be null");
        assertNotNull(createdGroup.getGroupId(), "Group ID should not be null");

        log.info("Successfully created group with OAuth2 authentication: {}", createdGroup.getGroupId());

        // Verify we can retrieve the group metadata
        var groupMetadata = registryService.getGroupMetadata(testGroupId);
        assertNotNull(groupMetadata, "Group metadata should not be null");

        log.info("Successfully retrieved group metadata: {}", utils.toPrettyJson(groupMetadata));
    }
}
