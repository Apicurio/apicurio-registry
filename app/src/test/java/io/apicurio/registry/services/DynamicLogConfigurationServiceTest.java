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

package io.apicurio.registry.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.logging.Level;

/**
 * Unit tests for the case-insensitive log level parsing used by
 * {@link DynamicLogConfigurationService}.
 */
public class DynamicLogConfigurationServiceTest {

    private String originalLogLevelProperty;

    /**
     * Verifies that uppercase log level values are parsed successfully.
     */
    @Test
    public void testUppercaseLogLevels() {
        String[] levels = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF", "ALL",
                "SEVERE", "WARNING", "CONFIG", "FINE", "FINER", "FINEST"};
        for (String level : levels) {
            Assertions.assertDoesNotThrow(
                    () -> Level.parse(level.toUpperCase(Locale.ROOT)),
                    "Should parse uppercase level: " + level);
        }
    }

    /**
     * Verifies that lowercase log level values are parsed successfully
     * after case normalization.
     */
    @Test
    public void testLowercaseLogLevels() {
        String[] levels = {"trace", "debug", "info", "warn", "error", "off", "all",
                "severe", "warning", "config", "fine", "finer", "finest"};
        for (String level : levels) {
            Assertions.assertDoesNotThrow(
                    () -> Level.parse(level.toUpperCase(Locale.ROOT)),
                    "Should parse lowercase level: " + level);
        }
    }

    /**
     * Verifies that mixed-case log level values are parsed successfully
     * after case normalization.
     */
    @Test
    public void testMixedCaseLogLevels() {
        String[] levels = {"Debug", "iNfO", "WaRn", "ErRoR", "TrAcE"};
        for (String level : levels) {
            Assertions.assertDoesNotThrow(
                    () -> Level.parse(level.toUpperCase(Locale.ROOT)),
                    "Should parse mixed-case level: " + level);
        }
    }

    /**
     * Verifies that invalid log level values still throw an exception.
     */
    @Test
    public void testInvalidLogLevels() {
        String[] levels = {"INVALID", "NOTAREALEVEL", "FOO"};
        for (String level : levels) {
            Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> Level.parse(level.toUpperCase(Locale.ROOT)),
                    "Should reject invalid level: " + level);
        }
    }

    @AfterEach
    void tearDown() {
        if (originalLogLevelProperty == null) {
            System.clearProperty("apicurio.log.level");
        } else {
            System.setProperty("apicurio.log.level", originalLogLevelProperty);
        }
    }

    @BeforeEach
    void setUp() {
        originalLogLevelProperty = System.getProperty("apicurio.log.level");
    }

    @Test
    void testResolveConfiguredLogLevelReturnsEmptyWhenUnset() {
        Assertions.assertTrue(DynamicLogConfigurationService.resolveConfiguredLogLevel().isEmpty());
    }

    @Test
    void testResolveConfiguredLogLevelReturnsExplicitValue() {
        System.setProperty("apicurio.log.level", "debug");

        Assertions.assertTrue(DynamicLogConfigurationService.resolveConfiguredLogLevel().isPresent());
        Assertions.assertEquals("debug", DynamicLogConfigurationService.resolveConfiguredLogLevel().orElseThrow());
    }
}
