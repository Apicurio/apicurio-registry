package io.apicurio.registry.mcptools.compatibility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPointersTest {

    @Test
    void testAppendEscapesPropertyNames() {
        assertEquals("/inputSchema/properties/a~1b~0c",
                JsonPointers.append("/inputSchema", "properties", "a/b~c"));
    }

    @Test
    void testAppendToDocumentRoot() {
        assertEquals("/outputSchema", JsonPointers.append("", "outputSchema"));
    }

    @Test
    void testPointerInsideNodeIsAtOrBelowIt() {
        assertTrue(JsonPointers.isAtOrBelow("/inputSchema/properties/a/type", "/inputSchema/properties/a"));
        assertTrue(JsonPointers.isAtOrBelow("/inputSchema/properties/a", "/inputSchema/properties/a"));
        assertTrue(JsonPointers.isAtOrBelow("/inputSchema/required/0", ""));
    }

    @Test
    void testSiblingSharingNamePrefixIsNotBelowNode() {
        assertFalse(JsonPointers.isAtOrBelow("/inputSchema/properties/ab", "/inputSchema/properties/a"));
    }

    @Test
    void testAncestorIsNotBelowNode() {
        assertFalse(JsonPointers.isAtOrBelow("/inputSchema", "/inputSchema/properties"));
    }

    @Test
    void testTokensAreComparedDecoded() {
        assertTrue(JsonPointers.isAtOrBelow("/inputSchema/properties/a~1b/type",
                "/inputSchema/properties/a~1b"));
        assertFalse(JsonPointers.isAtOrBelow("/inputSchema/properties/a/b", "/inputSchema/properties/a~1b"));
    }
}
