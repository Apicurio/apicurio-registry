package io.apicurio.registry.rest.wellknown;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.mcptools.McpToolsConfig;
import io.apicurio.registry.mcptools.compatibility.CrossToolCompatibilityService;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.rest.v3.beans.McpToolSearchResult;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WellKnownResourceImplCompatibleToolsTest {

    private static final String GROUP = "tools";
    private static final String SOURCE = "source";

    private static final String SOURCE_TOOL = """
            {"name": "source", "inputSchema": {"type": "object"},
             "outputSchema": {"type": "object", "properties": {"records": {"type": "array"}},
                              "required": ["records"], "additionalProperties": false}}
            """;

    private static final String COMPATIBLE_TOOL = """
            {"name": "compatible", "inputSchema": {"type": "object",
             "properties": {"records": {"type": "array"}}, "required": ["records"]}}
            """;

    private static final String INCOMPATIBLE_TOOL = """
            {"name": "incompatible", "inputSchema": {"type": "object",
             "properties": {"records": {"type": "string"}}}}
            """;

    private WellKnownResourceImpl resource;
    private RegistryStorage storage;

    @BeforeEach
    void setUp() {
        storage = mock(RegistryStorage.class);
        McpToolsConfig mcpToolsConfig = mock(McpToolsConfig.class);
        when(mcpToolsConfig.isEnabled()).thenReturn(true);

        resource = new WellKnownResourceImpl();
        resource.storage = storage;
        resource.mcpToolsConfig = mcpToolsConfig;
        resource.crossToolCompatibility = new CrossToolCompatibilityService();

        when(storage.getBranchTip(any(GA.class), any(BranchId.class),
                eq(RetrievalBehavior.SKIP_DISABLED_LATEST)))
                .thenAnswer(invocation -> new GAV(invocation.<GA>getArgument(0), "1"));
        when(storage.getArtifactVersionMetaData(eq(GROUP), anyString(), eq("1")))
                .thenReturn(ArtifactVersionMetaDataDto.builder().artifactType(ArtifactType.MCP_TOOL).build());
        storeTool(SOURCE, SOURCE_TOOL);
    }

    @Test
    void testReturnsOnlyCompatibleCandidatesAndNeverTheSource() {
        storeTool("compatible", COMPATIBLE_TOOL);
        storeTool("incompatible", INCOMPATIBLE_TOOL);
        scan(SOURCE, "compatible", "incompatible");

        assertEquals(List.of("compatible"), compatibleToolIds());
    }

    @Test
    void testSkipsCandidateWithoutEnabledVersion() {
        storeTool("compatible", COMPATIBLE_TOOL);
        when(storage.getBranchTip(eq(new GA(GROUP, "disabled")), any(BranchId.class),
                eq(RetrievalBehavior.SKIP_DISABLED_LATEST)))
                .thenThrow(new VersionNotFoundException(GROUP, "disabled", "<tip of the branch 'latest'>"));
        scan("disabled", "compatible");

        assertEquals(List.of("compatible"), compatibleToolIds());
    }

    @Test
    void testSkipsCandidateDeletedAfterTheSearch() {
        storeTool("compatible", COMPATIBLE_TOOL);
        when(storage.getArtifactVersionContent(GROUP, "deleted", "1"))
                .thenThrow(new ArtifactNotFoundException(GROUP, "deleted"));
        scan("deleted", "compatible");

        assertEquals(List.of("compatible"), compatibleToolIds());
    }

    @Test
    void testPropagatesOtherStorageFailures() {
        when(storage.getArtifactVersionContent(GROUP, "broken", "1"))
                .thenThrow(new RegistryStorageException("database unavailable"));
        scan("broken");

        assertThrows(RegistryStorageException.class,
                () -> resource.findCompatibleTools(GROUP, SOURCE, null, 0, 20));
    }

    @Test
    void testExcludesCandidateWhoseContentIsNotJson() {
        storeTool("compatible", COMPATIBLE_TOOL);
        storeTool("garbled", "{not json");
        scan("garbled", "compatible");

        assertEquals(List.of("compatible"), compatibleToolIds());
    }

    @Test
    void testDoesNotScanWhenSourceHasNoOutputSchema() {
        storeTool(SOURCE, "{\"name\": \"source\", \"inputSchema\": {\"type\": \"object\"}}");

        assertEquals(List.of(), compatibleToolIds());
        verify(storage, never()).searchArtifacts(anySet(), any(), any(), anyInt(), anyInt(), eq(false));
    }

    @Test
    void testComparesSourceWhoseOutputSchemaDeclaresNoProperties() {
        storeTool(SOURCE, """
                {"name": "source", "inputSchema": {"type": "object"},
                 "outputSchema": {"type": "object", "additionalProperties": false}}
                """);
        storeTool("any-object", "{\"name\": \"any\", \"inputSchema\": {\"type\": \"object\"}}");
        storeTool("requires-x", """
                {"name": "requires-x", "inputSchema": {"type": "object",
                 "properties": {"x": {"type": "string"}}, "required": ["x"]}}
                """);
        scan("any-object", "requires-x");

        assertEquals(List.of("any-object"), compatibleToolIds());
    }

    private void storeTool(String artifactId, String content) {
        when(storage.getArtifactVersionContent(GROUP, artifactId, "1"))
                .thenReturn(StoredArtifactVersionDto.builder().content(ContentHandle.create(content)).build());
    }

    private void scan(String... artifactIds) {
        List<SearchedArtifactDto> candidates = Arrays.stream(artifactIds)
                .map(artifactId -> SearchedArtifactDto.builder().groupId(GROUP).artifactId(artifactId)
                        .name(artifactId).createdOn(new Date()).build())
                .toList();
        when(storage.searchArtifacts(anySet(), eq(OrderBy.createdOn), eq(OrderDirection.desc), eq(0),
                anyInt(), eq(false)))
                .thenReturn(ArtifactSearchResultsDto.builder().artifacts(candidates).count(candidates.size())
                        .build());
    }

    private List<String> compatibleToolIds() {
        return resource.findCompatibleTools(GROUP, SOURCE, null, 0, 20).getTools().stream()
                .map(McpToolSearchResult::getArtifactId)
                .toList();
    }
}
