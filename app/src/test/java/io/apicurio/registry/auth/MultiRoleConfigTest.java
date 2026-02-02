package io.apicurio.registry.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for multi-role and multi-scope configuration parsing.
 * Uses MultiRoleAuthTestProfile to configure comma-separated role values
 * and client-specific scopes.
 */
@QuarkusTest
@TestProfile(MultiRoleAuthTestProfile.class)
public class MultiRoleConfigTest {

    @Inject
    AuthConfig authConfig;

    @Test
    public void testMultipleAdminRoles() {
        Set<String> adminRoles = authConfig.getAdminRoles();
        assertNotNull(adminRoles);
        assertEquals(3, adminRoles.size());
        assertTrue(adminRoles.contains("sr-admin"));
        assertTrue(adminRoles.contains("azure-admin-group"));
        assertTrue(adminRoles.contains("AppRole.Admin"));
    }

    @Test
    public void testMultipleDeveloperRoles() {
        Set<String> developerRoles = authConfig.getDeveloperRoles();
        assertNotNull(developerRoles);
        assertEquals(3, developerRoles.size());
        assertTrue(developerRoles.contains("sr-developer"));
        assertTrue(developerRoles.contains("azure-dev-group"));
        assertTrue(developerRoles.contains("AppRole.Developer"));
    }

    @Test
    public void testMultipleReadOnlyRoles() {
        Set<String> readOnlyRoles = authConfig.getReadOnlyRoles();
        assertNotNull(readOnlyRoles);
        assertEquals(3, readOnlyRoles.size());
        assertTrue(readOnlyRoles.contains("sr-readonly"));
        assertTrue(readOnlyRoles.contains("azure-readonly-group"));
        assertTrue(readOnlyRoles.contains("AppRole.Reader"));
    }

    @Test
    public void testMultipleAdminOverrideRoles() {
        Set<String> adminOverrideRoles = authConfig.getAdminOverrideRoles();
        assertNotNull(adminOverrideRoles);
        assertEquals(2, adminOverrideRoles.size());
        assertTrue(adminOverrideRoles.contains("sr-admin"));
        assertTrue(adminOverrideRoles.contains("azure-admin-group"));
    }

    @Test
    public void testDefaultScopeForUnknownClient() {
        String scope = authConfig.getScopeForClient("unknown-client");
        assertNotNull(scope);
        assertEquals("api://default/.default", scope);
    }

    @Test
    public void testClientSpecificScope() {
        String scope = authConfig.getScopeForClient("test-client-1");
        assertNotNull(scope);
        assertEquals("api://app1/.default", scope);
    }

    @Test
    public void testClientSpecificScopeWithMultipleScopes() {
        // Comma-separated scopes should be converted to space-separated (OAuth2 standard)
        String scope = authConfig.getScopeForClient("test-client-2");
        assertNotNull(scope);
        assertEquals("api://app2/.default api://shared/.default", scope);
    }

    @Test
    public void testNullClientIdUsesDefaultScope() {
        String scope = authConfig.getScopeForClient(null);
        assertNotNull(scope);
        assertEquals("api://default/.default", scope);
    }
}
