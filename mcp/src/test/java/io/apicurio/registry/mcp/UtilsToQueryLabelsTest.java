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
package io.apicurio.registry.mcp;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Utils#toQueryLabels(String)}.
 *
 * <p>Verifies that the method correctly converts a JSON map of labels into
 * a {@code String[]} of {@code "key:value"} entries suitable for the
 * Apicurio Registry search API query parameters.</p>
 */
@QuarkusTest
public class UtilsToQueryLabelsTest {

    @Inject
    Utils utils;

    @Test
    public void testNullInputReturnsNull() {
        assertNull(utils.toQueryLabels(null));
    }

    @Test
    public void testEmptyJsonObjectReturnsEmptyArray() {
        String[] result = utils.toQueryLabels("{}");
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void testSingleLabel() {
        String[] result = utils.toQueryLabels("{\"env\":\"prod\"}");
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("env:prod", result[0]);
    }

    @Test
    public void testMultipleLabels() {
        String[] result = utils.toQueryLabels("{\"env\":\"prod\",\"team\":\"backend\"}");
        assertNotNull(result);
        assertEquals(2, result.length);
        // Sort to make assertion order-independent (HashMap has no guaranteed order)
        java.util.Arrays.sort(result);
        assertEquals("env:prod", result[0]);
        assertEquals("team:backend", result[1]);
    }

    @Test
    public void testReturnTypeIsStringArray() {
        String[] result = utils.toQueryLabels("{\"key\":\"value\"}");
        // This assertion verifies the fix: .toArray(String[]::new) produces String[],
        // NOT Object[]. Before the fix, this line would throw ClassCastException.
        assertInstanceOf(String[].class, result);
    }

    @Test
    public void testInvalidJsonThrowsToolCallException() {
        assertThrows(ToolCallException.class, () -> utils.toQueryLabels("{broken"));
    }

    @Test
    public void testJsonArrayThrowsToolCallException() {
        assertThrows(ToolCallException.class, () -> utils.toQueryLabels("[1,2,3]"));
    }

    @Test
    public void testNonStringValueThrowsToolCallException() {
        assertThrows(ToolCallException.class, () -> utils.toQueryLabels("{\"count\":42}"));
    }

    @Test
    public void testEmptyStringThrowsToolCallException() {
        assertThrows(ToolCallException.class, () -> utils.toQueryLabels(""));
    }

    @Test
    public void testLabelWithColonInValue() {
        // Labels with colons in values should still produce correct key:value format
        String[] result = utils.toQueryLabels("{\"url\":\"http://example.com\"}");
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("url:http://example.com", result[0]);
    }

    @Test
    public void testLabelWithEmptyValue() {
        String[] result = utils.toQueryLabels("{\"tag\":\"\"}");
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("tag:", result[0]);
    }
}
