/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.config;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeprecatedPropertiesCheck}.
 */
public class DeprecatedPropertiesCheckTest {

    private Config config;
    private Logger log;
    private DeprecatedPropertiesCheck check;

    @BeforeEach
    void setUp() {
        config = mock(Config.class);
        log = mock(Logger.class);

        check = new DeprecatedPropertiesCheck(List.of(
                new DeprecatedPropertiesCheck.DeprecatedPropertyDef(
                        "apicurio.example.old", "apicurio.example.new", "3.3.0", "4.0.0", false),
                new DeprecatedPropertiesCheck.DeprecatedPropertyDef(
                        "apicurio.removed.old", "apicurio.removed.new", "2.5.0", "3.0.0", true),
                new DeprecatedPropertiesCheck.DeprecatedPropertyDef(
                        "apicurio.legacy.withoutreplacement", null, "2.0.0", "3.0.0", true)
        ));
        check.config = config;
        check.log = log;
    }

    @Test
    void testNoPropertiesSetPassesValidation() {
        when(config.getOptionalValue("apicurio.example.old", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.example.new", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.removed.old", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.removed.new", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.legacy.withoutreplacement", String.class)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> check.validate());
    }

    @Test
    void testPhase1DeprecatedPropertySetLogsWarning() {
        when(config.getOptionalValue("apicurio.example.old", String.class)).thenReturn(Optional.of("oldValue"));
        when(config.getOptionalValue("apicurio.example.new", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.removed.old", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.legacy.withoutreplacement", String.class)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> check.validate());

        verify(log).warn("Property '{}' is deprecated since {} and will be removed in {}. Use '{}' instead.",
                "apicurio.example.old", "3.3.0", "4.0.0", "apicurio.example.new");
    }

    @Test
    void testPhase1BothPropertiesSetLogsPrecedenceWarning() {
        when(config.getOptionalValue("apicurio.example.old", String.class)).thenReturn(Optional.of("oldValue"));
        when(config.getOptionalValue("apicurio.example.new", String.class)).thenReturn(Optional.of("newValue"));
        when(config.getOptionalValue("apicurio.removed.old", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.legacy.withoutreplacement", String.class)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> check.validate());

        verify(log).warn("Both '{}' (deprecated since {}) and '{}' are configured. '{}' will take precedence.",
                "apicurio.example.old", "3.3.0", "apicurio.example.new", "apicurio.example.new");
    }

    @Test
    void testPhase2RemovedPropertySetThrowsException() {
        when(config.getOptionalValue("apicurio.example.old", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.removed.old", String.class)).thenReturn(Optional.of("removedValue"));
        when(config.getOptionalValue("apicurio.removed.new", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.legacy.withoutreplacement", String.class)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> check.validate());

        assertTrue(ex.getMessage().contains("apicurio.removed.old"));
        assertTrue(ex.getMessage().contains("was removed in 3.0.0"));
        verify(log).error("Property '{}' was removed in {}. Use '{}' instead.",
                "apicurio.removed.old", "3.0.0", "apicurio.removed.new");
    }

    @Test
    void testPhase2RemovedPropertyBothSetThrowsException() {
        when(config.getOptionalValue("apicurio.example.old", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.removed.old", String.class)).thenReturn(Optional.of("removedValue"));
        when(config.getOptionalValue("apicurio.removed.new", String.class)).thenReturn(Optional.of("newValue"));
        when(config.getOptionalValue("apicurio.legacy.withoutreplacement", String.class)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> check.validate());

        assertTrue(ex.getMessage().contains("apicurio.removed.old"));
        verify(log).error("Property '{}' was removed in {}. Use '{}' instead.",
                "apicurio.removed.old", "3.0.0", "apicurio.removed.new");
    }

    @Test
    void testPhase2RemovedPropertyNullReplacementNameMessage() {
        when(config.getOptionalValue("apicurio.example.old", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.removed.old", String.class)).thenReturn(Optional.empty());
        when(config.getOptionalValue("apicurio.legacy.withoutreplacement", String.class)).thenReturn(Optional.of("val"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> check.validate());

        assertTrue(ex.getMessage().contains("Property 'apicurio.legacy.withoutreplacement' was removed in 3.0.0."));
        verify(log).error("Property 'apicurio.legacy.withoutreplacement' was removed in 3.0.0.");
    }

    @Test
    void testGetValuePrecedenceResolution() {
        when(config.getOptionalValue("apicurio.example.new", String.class)).thenReturn(Optional.of("newVal"));
        when(config.getOptionalValue("apicurio.example.old", String.class)).thenReturn(Optional.of("oldVal"));

        Optional<String> val = check.getValue("apicurio.example.old", String.class);

        assertTrue(val.isPresent());
        assertEquals("newVal", val.get());

        // Test fallback when replacement is empty
        when(config.getOptionalValue("apicurio.example.new", String.class)).thenReturn(Optional.empty());
        Optional<String> fallbackVal = check.getValue("apicurio.example.old", String.class);

        assertTrue(fallbackVal.isPresent());
        assertEquals("oldVal", fallbackVal.get());
    }

    @Test
    void testDefaultConstructorRegistersKnownProperties() {
        DeprecatedPropertiesCheck defaultCheck = new DeprecatedPropertiesCheck();
        assertEquals(5, defaultCheck.getRegistry().size());
    }
}
