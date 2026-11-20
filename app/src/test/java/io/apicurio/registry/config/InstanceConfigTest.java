package io.apicurio.registry.config;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstanceConfigTest {

    private static InstanceConfig named(String name) {
        InstanceConfig config = new InstanceConfig();
        config.name = Optional.ofNullable(name);
        return config;
    }

    @Test
    void configuredInstanceNameTakesPrecedenceOverTheDeprecatedName() {
        assertEquals("Acme", named("Acme").nameOr(Optional.of("Legacy")));
    }

    @Test
    void deprecatedNameIsStillUsedOnItsOwn() {
        assertEquals("Legacy", named(null).nameOr(Optional.of("Legacy")));
    }

    @Test
    void defaultWhenNothingIsSet() {
        assertEquals(InstanceConfig.DEFAULT_NAME, named(null).nameOr(Optional.empty()));
        assertEquals("Apicurio Registry", InstanceConfig.DEFAULT_NAME);
    }
}
