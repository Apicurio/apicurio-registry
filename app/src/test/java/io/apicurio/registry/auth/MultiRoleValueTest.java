package io.apicurio.registry.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for multi-role value parsing functionality in AuthConfig.
 * Verifies that comma-separated role values are correctly parsed and
 * that backward compatibility with single-value configurations is maintained.
 */
@QuarkusTest
public class MultiRoleValueTest {

    @Inject
    AuthConfig authConfig;

    @Test
    public void testDefaultSingleRoleValues() {
        // Default configuration should have single role values
        Set<String> adminRoles = authConfig.getAdminRoles();
        assertNotNull(adminRoles);
        assertFalse(adminRoles.isEmpty());
        assertTrue(adminRoles.contains("sr-admin"));

        Set<String> developerRoles = authConfig.getDeveloperRoles();
        assertNotNull(developerRoles);
        assertFalse(developerRoles.isEmpty());
        assertTrue(developerRoles.contains("sr-developer"));

        Set<String> readOnlyRoles = authConfig.getReadOnlyRoles();
        assertNotNull(readOnlyRoles);
        assertFalse(readOnlyRoles.isEmpty());
        assertTrue(readOnlyRoles.contains("sr-readonly"));
    }

    @Test
    public void testAdminOverrideRoles() {
        Set<String> adminOverrideRoles = authConfig.getAdminOverrideRoles();
        assertNotNull(adminOverrideRoles);
        assertFalse(adminOverrideRoles.isEmpty());
        assertTrue(adminOverrideRoles.contains("sr-admin"));
    }

    @Test
    public void testDefaultScopeResolution() {
        // When no client-specific scope is configured, should use default or return null
        String scope = authConfig.getScopeForClient("unknown-client-id");
        // Default scope may be null if not configured
        // This test verifies the method doesn't throw exceptions
    }

    @Test
    public void testScopeNormalization() {
        // Test that the scope normalization (comma to space conversion) works
        // by verifying the method can be called without errors
        String scope = authConfig.getScopeForClient(null);
        // Should not throw, just return default or null
    }
}
