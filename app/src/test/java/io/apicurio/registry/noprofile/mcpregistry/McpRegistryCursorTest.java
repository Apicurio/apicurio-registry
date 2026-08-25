package io.apicurio.registry.noprofile.mcpregistry;

import io.apicurio.registry.mcpregistry.McpRegistryCursor;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the offset-to-cursor translation used by the MCP Registry API.
 */
public class McpRegistryCursorTest {

    private static final String FILTERS = "servers|weather||";

    @Test
    public void testRoundTrip() {
        assertEquals(30, McpRegistryCursor.decode(McpRegistryCursor.encode(30, FILTERS), FILTERS));
        assertEquals(0, McpRegistryCursor.decode(McpRegistryCursor.encode(0, FILTERS), FILTERS));
    }

    @Test
    public void testAbsentCursorMeansTheFirstPage() {
        assertEquals(0, McpRegistryCursor.decode(null, FILTERS));
        assertEquals(0, McpRegistryCursor.decode("", FILTERS));
    }

    @Test
    public void testCursorIsOpaque() {
        String cursor = McpRegistryCursor.encode(30, FILTERS);
        assertNotEquals("30", cursor);
        assertEquals(-1, cursor.indexOf("30"));
    }

    @Test
    public void testCursorFromDifferentFiltersIsRejected() {
        String cursor = McpRegistryCursor.encode(30, FILTERS);
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> McpRegistryCursor.decode(cursor, "servers|something-else||"));
        assertEquals("Invalid cursor: it was issued for a different set of search filters",
                e.getMessage());
    }

    @Test
    public void testMalformedCursorIsRejected() {
        assertThrows(BadRequestException.class, () -> McpRegistryCursor.decode("!!!not-base64!!!", FILTERS));
        assertThrows(BadRequestException.class,
                () -> McpRegistryCursor.decode("bm90LWEtY3Vyc29y", FILTERS));
    }

    @Test
    public void testCursorFromAnOlderEncodingIsRejected() {
        // "v0:30:abcdef0123456789" - a cursor whose format prefix we no longer issue
        String legacy = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("v0:30:abcdef0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(BadRequestException.class, () -> McpRegistryCursor.decode(legacy, FILTERS));
    }
}
