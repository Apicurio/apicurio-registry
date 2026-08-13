package io.apicurio.registry.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for RegistryConfigSource.
 * Tests the ConfigSource SPI contract: getPropertyNames() must return property keys,
 * and must be safe to call before getProperties().
 */
public class RegistryConfigSourceTest {

    private RegistryConfigSource configSource;

    @BeforeEach
    public void setUp() {
        configSource = new RegistryConfigSource();
    }

    /**
     * Test that getPropertyNames() returns property keys (not values).
     * This verifies the ConfigSource SPI contract: getPropertyNames() must return
     * a Set of property names (keys), not the property values.
     */
    @Test
    public void testGetPropertyNamesReturnsKeys() throws Exception {
        Map<String, String> properties = new HashMap<>();
        properties.put("%prod.my.key1", "value1");
        properties.put("%prod.my.key2", "value2");
        properties.put("%prod.another.setting", "another_value");
        
        setPropertiesViaReflection(properties);

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
     * Test that getPropertyNames() is safe to call before getProperties().
     * This verifies that getPropertyNames() does not throw NullPointerException
     * when the internal properties field is null (before lazy initialization).
     * The method must handle the lazy initialization safely.
     */
    @Test
    public void testGetPropertyNamesBeforeGetProperties() throws Exception {
        setPropertiesViaReflection(null);

        Set<String> propertyNames = Assertions.assertDoesNotThrow(() -> {
            return configSource.getPropertyNames();
        }, "getPropertyNames() should not throw NPE when called before getProperties()");

        Assertions.assertNotNull(propertyNames,
                "getPropertyNames() should return a Set, not null");
        Assertions.assertTrue(propertyNames instanceof Set,
                "getPropertyNames() should return a Set instance");
    }

    /**
     * Helper method to inject properties via reflection.
     * This allows unit tests to control the properties without relying on
     * System.getenv() which is difficult to mock.
     */
    private void setPropertiesViaReflection(Map<String, String> props) throws Exception {
        Field propertiesField = RegistryConfigSource.class.getDeclaredField("properties");
        propertiesField.setAccessible(true);
        propertiesField.set(configSource, props);
    }
}
