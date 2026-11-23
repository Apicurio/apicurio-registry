package io.apicurio.registry.rest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.apicurio.common.apps.config.DynamicConfigPropertyDef;
import io.apicurio.registry.config.ExperimentalFeaturesConfig;
import io.apicurio.registry.services.LogLevelValidator;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The experimental gate must read a value the way the property is read back, so every spelling that
 * MicroProfile Config treats as true is refused while the gate is closed.
 */
class DynamicConfigPropertyValidatorTest {

    private static final String PROPERTY = "apicurio.ui.features.agents.enabled";

    private DynamicConfigPropertyValidator validator;
    private ExperimentalFeaturesConfig experimentalFeatures;
    private DynamicConfigPropertyDef propertyDef;

    @BeforeEach
    void setup() {
        experimentalFeatures = mock(ExperimentalFeaturesConfig.class);
        when(experimentalFeatures.canEnable(PROPERTY)).thenReturn(false);
        propertyDef = mock(DynamicConfigPropertyDef.class);
        when(propertyDef.getName()).thenReturn(PROPERTY);
        when(propertyDef.isValidValue(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        validator = new DynamicConfigPropertyValidator();
        validator.experimentalFeatures = experimentalFeatures;
        validator.logLevelValidator = mock(LogLevelValidator.class);
        validator.config = new SmallRyeConfigBuilder().build();
    }

    @ParameterizedTest
    @ValueSource(strings = { "true", "TRUE", "1", "yes", "on", "y" })
    void trueSpellingsAreRefusedWhileGateIsClosed(String value) {
        assertThrows(ConflictException.class, () -> validator.validate(propertyDef, value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "false", "0", "no", "off" })
    void falseSpellingsArePassedWhileGateIsClosed(String value) {
        assertDoesNotThrow(() -> validator.validate(propertyDef, value));
    }

    @Test
    void trueIsAllowedWhenGateIsOpen() {
        when(experimentalFeatures.canEnable(PROPERTY)).thenReturn(true);
        assertDoesNotThrow(() -> validator.validate(propertyDef, "on"));
    }
}
