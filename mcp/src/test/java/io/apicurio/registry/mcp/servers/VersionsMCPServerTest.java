package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionState;
import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionsMCPServerTest {

    private static class CapturingRegistryService extends RegistryService {

        private boolean called;
        private VersionState capturedState;
        private final List<SearchedVersion> result = Collections.emptyList();

        @Override
        public List<SearchedVersion> searchVersions(
                String groupId,
                String artifactId,
                String artifactType,
                String name,
                String description,
                String jsonLabels,
                String order,
                String versionOrderBy,
                VersionState state
        ) {
            called = true;
            capturedState = state;
            return result;
        }
    }

    private VersionsMCPServer server;
    private CapturingRegistryService service;

    @BeforeEach
    public void setUp() {
        server = new VersionsMCPServer();
        service = new CapturingRegistryService();
        server.service = service;
    }

    private List<SearchedVersion> search(String versionState) {
        return server.search_versions(
                "test-group",
                "test-artifact",
                null,
                null,
                null,
                null,
                versionState,
                "asc",
                "globalId");
    }

    @Test
    public void testNullVersionStatePassesNullFilter() {
        List<SearchedVersion> result = search(null);

        assertTrue(service.called);
        assertNull(service.capturedState);
        assertSame(service.result, result);
    }

    @Test
    public void testValidVersionStateIsResolvedCaseInsensitively() {
        search("ENABLED");
        assertEquals(VersionState.ENABLED, service.capturedState);

        search("disabled");
        assertEquals(VersionState.DISABLED, service.capturedState);

        search("Deprecated");
        assertEquals(VersionState.DEPRECATED, service.capturedState);

        search("draft");
        assertEquals(VersionState.DRAFT, service.capturedState);
    }

    @Test
    public void testVersionStateWithSurroundingWhitespaceIsAccepted() {
        search("  ENABLED  ");
        assertEquals(VersionState.ENABLED, service.capturedState);

        search(" draft ");
        assertEquals(VersionState.DRAFT, service.capturedState);
    }

    @Test
    public void testInvalidVersionStateThrowsToolCallException() {
        ToolCallException exception = assertThrows(ToolCallException.class, () -> search("ENABLE"));

        assertEquals("Invalid versionState: 'ENABLE'. Accepted values (case-insensitive): "
                + Arrays.toString(VersionState.values()),
                exception.getMessage());
        assertFalse(service.called);
    }

    @Test
    public void testArbitraryInvalidVersionStateThrowsToolCallException() {
        ToolCallException exception = assertThrows(ToolCallException.class, () -> search("INVALID_STATE"));

        assertTrue(exception.getMessage().contains("Invalid versionState: 'INVALID_STATE'"));
        assertFalse(service.called);
    }
}
