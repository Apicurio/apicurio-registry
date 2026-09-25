package io.apicurio.registry.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for RegistryConfigSource.
 * Tests the ConfigSource SPI contract: getPropertyNames() must return property keys,
 * and must be safe to call on a source with no properties.
 */
public class RegistryConfigSourceTest {

    /**
     * Test that getPropertyNames() returns property keys (not values).
     * This verifies the ConfigSource SPI contract: getPropertyNames() must return
     * a Set of property names (keys), not the property values.
     */
    @Test
    public void testGetPropertyNamesReturnsKeys() {
        Map<String, String> properties = new HashMap<>();
        properties.put("%prod.my.key1", "value1");
        properties.put("%prod.my.key2", "value2");
        properties.put("%prod.another.setting", "another_value");

        RegistryConfigSource configSource = new RegistryConfigSource(properties);

        Set<String> propertyNames = configSource.getPropertyNames();

        Assertions.assertTrue(propertyNames.contains("%prod.my.key1"),
                "Property names should contain the key '%prod.my.key1'");
        Assertions.assertTrue(propertyNames.contains("%prod.my.key2"),
                "Property names should contain the key '%prod.my.key2'");
        Assertions.assertTrue(propertyNames.contains("%prod.another.setting"),
                "Property names should contain the key '%prod.another.setting'");

        Assertions.assertFalse(propertyNames.contains("value1"),
                "Property names should NOT contain the value 'value1'");
        Assertions.assertFalse(propertyNames.contains("value2"),
                "Property names should NOT contain the value 'value2'");
        Assertions.assertFalse(propertyNames.contains("another_value"),
                "Property names should NOT contain the value 'another_value'");
    }

    /**
     * Test that getPropertyNames() is safe to call on a source with an empty property map (no
     * REGISTRY_PROPERTIES_PREFIX-matching env vars found).
     */
    @Test
    public void testGetPropertyNamesWithNoProperties() {
        RegistryConfigSource configSource = new RegistryConfigSource(new HashMap<>());

        Set<String> propertyNames = Assertions.assertDoesNotThrow(configSource::getPropertyNames,
                "getPropertyNames() should not throw when there are no properties");

        Assertions.assertNotNull(propertyNames, "getPropertyNames() should return a Set, not null");
        Assertions.assertTrue(propertyNames.isEmpty(), "getPropertyNames() should be empty");
    }

    /**
     * The public, no-arg constructor computes properties from real environment variables - this
     * just verifies it doesn't throw and always returns a non-null, usable ConfigSource, since
     * REGISTRY_PROPERTIES_PREFIX won't normally be set in a test environment.
     */
    @Test
    public void testDefaultConstructorComputesFromEnvironment() {
        RegistryConfigSource configSource = new RegistryConfigSource();

        Assertions.assertNotNull(configSource.getProperties());
        Assertions.assertNotNull(configSource.getPropertyNames());
    }
}
