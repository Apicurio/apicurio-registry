package io.apicurio.registry.mcp.servers;

import io.apicurio.registry.mcp.RegistryService;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionState;
import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionsMCPServerTest {

    private VersionsMCPServer server;
    private final AtomicReference<VersionState> capturedState = new AtomicReference<>();

    @BeforeEach
    public void setUp() throws Exception {
        server = new VersionsMCPServer();
        RegistryService service = new RegistryService() {
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
                capturedState.set(state);
                return Collections.emptyList();
            }
        };

        Field serviceField = VersionsMCPServer.class.getDeclaredField("service");
        serviceField.setAccessible(true);
        serviceField.set(server, service);
        capturedState.set(null);
    }

    @Test
    public void testSearchVersionsWithValidVersionState() {
        server.search_versions("group1", "artifact1", null, null, null, null, "enabled", null, null);
        assertEquals(VersionState.ENABLED, capturedState.get());

        server.search_versions("group1", "artifact1", null, null, null, null, "DRAFT", null, null);
        assertEquals(VersionState.DRAFT, capturedState.get());

        server.search_versions("group1", "artifact1", null, null, null, null, "Deprecated", null, null);
        assertEquals(VersionState.DEPRECATED, capturedState.get());

        server.search_versions("group1", "artifact1", null, null, null, null, "DISABLED", null, null);
        assertEquals(VersionState.DISABLED, capturedState.get());
    }

    @Test
    public void testSearchVersionsWithNullOrBlankVersionState() {
        server.search_versions("group1", "artifact1", null, null, null, null, null, null, null);
        assertNull(capturedState.get());

        server.search_versions("group1", "artifact1", null, null, null, null, "   ", null, null);
        assertNull(capturedState.get());
    }

    @Test
    public void testSearchVersionsWithInvalidVersionStateThrows() {
        ToolCallException ex = assertThrows(ToolCallException.class, () ->
                server.search_versions("group1", "artifact1", null, null, null, null, "ENABLE", null, null));

        assertTrue(ex.getMessage().contains("Invalid version state: 'ENABLE'"));
        assertTrue(ex.getMessage().contains("Accepted values (case-insensitive):"));
        assertTrue(ex.getMessage().contains("ENABLED"));
        assertTrue(ex.getMessage().contains("DISABLED"));
        assertTrue(ex.getMessage().contains("DEPRECATED"));
        assertTrue(ex.getMessage().contains("DRAFT"));
    }
}
