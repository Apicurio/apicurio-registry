package io.apicurio.registry.agents.rest.wellknown;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.agents.mcptools.McpToolsConfig;
import io.apicurio.registry.agents.mcptools.compatibility.CrossToolCompatibilityService;
import io.apicurio.registry.auth.ISearchAuthorizer;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.rest.InvalidParameterValueException;
import io.apicurio.registry.rest.v3.beans.McpCompatibleToolsResults;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityLimitation;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityReason;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityResult;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityResults;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilitySide;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityTotals;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityVerdict;
import io.apicurio.registry.rest.v3.beans.McpToolSearchResult;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolDiscoveryCompatibleToolsTest {

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

    private static final String INDETERMINATE_TOOL = """
            {"name": "indeterminate", "inputSchema": {"type": "object",
             "properties": {"records": {"type": "array", "minItems": 1}}}}
            """;

    private static final BigInteger FIRST = BigInteger.ZERO;
    private static final BigInteger PAGE = BigInteger.valueOf(20);
    private static final int SCAN_LIMIT = 3;

    private McpToolDiscovery resource;
    private RegistryStorage storage;

    @BeforeEach
    void setUp() {
        storage = mock(RegistryStorage.class);
        McpToolsConfig mcpToolsConfig = mock(McpToolsConfig.class);
        when(mcpToolsConfig.isEnabled()).thenReturn(true);

        resource = new McpToolDiscovery();
        resource.storage = storage;
        // Per-resource authorization disabled: searches delegate to storage
        resource.searchAuthorizer = new ISearchAuthorizer() {
            @Override
            public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
                    OrderDirection orderDir, int offset, int limit, boolean skipCount) {
                return storage.searchArtifacts(filters, orderBy, orderDir, offset, limit, skipCount);
            }

            @Override
            public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy,
                    OrderDirection orderDir, int offset, int limit) {
                return storage.searchGroups(filters, orderBy, orderDir, offset, limit);
            }

            @Override
            public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
                    OrderDirection orderDir, int offset, int limit, boolean skipCount) {
                return storage.searchVersions(filters, orderBy, orderDir, offset, limit, skipCount);
            }
        };
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
                () -> resource.findCompatibleTools(GROUP, SOURCE, null, FIRST, PAGE));
        assertThrows(RegistryStorageException.class, () -> compatibility(null, FIRST, PAGE));
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

    @Test
    void testCompatibilityReportsEveryVerdictWithItsReasonsAndLimitations() {
        storeCatalog();

        McpToolCompatibilityResults results = compatibility(null, FIRST, PAGE);

        assertEquals(List.of("compatible", "incompatible", "indeterminate", "garbled"), artifactIds(results));
        assertEquals(List.of(McpToolCompatibilityVerdict.COMPATIBLE, McpToolCompatibilityVerdict.INCOMPATIBLE,
                McpToolCompatibilityVerdict.INDETERMINATE, McpToolCompatibilityVerdict.INDETERMINATE),
                results.getResults().stream().map(McpToolCompatibilityResult::getVerdict).toList());
        assertEquals(4, results.getEvaluated());
        assertEquals(4, results.getCount());
        assertEquals(totals(1, 1, 2), results.getTotals());
        assertEquals(List.of(), results.getLimitations());

        McpToolCompatibilityResult compatible = results.getResults().get(0);
        assertEquals(List.of(), compatible.getReasons());
        assertEquals(List.of(), compatible.getLimitations());

        McpToolCompatibilityResult incompatible = results.getResults().get(1);
        assertEquals(List.of(McpToolCompatibilityReason.builder()
                .code("TYPE_NOT_ACCEPTED")
                .producerPointer("/outputSchema/properties/records/type")
                .consumerPointer("/inputSchema/properties/records/type")
                .message("The producer's value for 'records' is not accepted by the consumer")
                .build()), incompatible.getReasons());
        assertEquals(List.of(), incompatible.getLimitations());

        McpToolCompatibilityResult indeterminate = results.getResults().get(2);
        assertEquals(List.of(), indeterminate.getReasons());
        assertEquals(List.of(limitation("UNSUPPORTED_KEYWORD", McpToolCompatibilitySide.CONSUMER,
                "/inputSchema/properties/records/minItems", "'minItems' is not evaluated yet")),
                indeterminate.getLimitations());

        // A candidate whose content is not JSON is evaluated, and listed without a title or parameters.
        McpToolCompatibilityResult garbled = results.getResults().get(3);
        assertEquals(List.of(), garbled.getReasons());
        assertEquals(List.of(limitation("COMPARISON_FAILED", McpToolCompatibilitySide.CONSUMER, "",
                "The tool definition is not valid JSON")), garbled.getLimitations());
        assertNull(garbled.getTool().getTitle());
        assertEquals(List.of(), garbled.getTool().getParameters());
    }

    @Test
    void testCompatibleReturnsExactlyTheCandidatesThatCompatibilityFindsCompatible() {
        storeCatalog();

        List<McpToolSearchResult> compatibleInCompatibility = compatibility(null, FIRST, PAGE).getResults().stream()
                .filter(result -> result.getVerdict() == McpToolCompatibilityVerdict.COMPATIBLE)
                .map(McpToolCompatibilityResult::getTool)
                .toList();
        McpCompatibleToolsResults compatible = resource.findCompatibleTools(GROUP, SOURCE, null, FIRST, PAGE);

        assertEquals(List.of("compatible"), compatibleInCompatibility.stream()
                .map(McpToolSearchResult::getArtifactId).toList());
        assertEquals(compatibleInCompatibility, compatible.getTools());
        assertEquals(1, compatible.getCount());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "INDETERMINATE               | indeterminate garbled",
            "COMPATIBLE INCOMPATIBLE     | compatible incompatible",
            "INCOMPATIBLE INCOMPATIBLE   | incompatible",
            "INDETERMINATE COMPATIBLE    | compatible indeterminate garbled",
    })
    void testVerdictFilterSelectsTheResultsButNotTheTotals(String verdicts, String artifactIds) {
        storeCatalog();

        McpToolCompatibilityResults results = compatibility(List.of(verdicts.split(" ")), FIRST, PAGE);

        assertEquals(List.of(artifactIds.split(" ")), artifactIds(results));
        assertEquals(results.getResults().size(), results.getCount());
        assertEquals(4, results.getEvaluated());
        assertEquals(totals(1, 1, 2), results.getTotals());
    }

    @Test
    void testEveryVerdictIsReturnedByDefault() {
        storeCatalog();

        assertEquals(4, compatibility(null, FIRST, PAGE).getCount());
        assertEquals(4, compatibility(List.of(), FIRST, PAGE).getCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"compatible", "MAYBE", ""})
    void testUnknownVerdictIsRejected(String verdict) {
        assertThrows(InvalidParameterValueException.class,
                () -> compatibility(List.of("COMPATIBLE", verdict), FIRST, PAGE));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 2, compatible incompatible",
            "-5, 2, compatible incompatible",
            "1, -5, incompatible",
            "1, 2147483647, incompatible indeterminate garbled",
            "0, 100000000000000000000, compatible incompatible indeterminate garbled",
            "100000000000000000000, 20, ''",
            "0, 0, ''",
    })
    void testPaginationIsNormalized(String offset, String limit, String artifactIds) {
        storeCatalog();

        McpToolCompatibilityResults results = compatibility(null, new BigInteger(offset), new BigInteger(limit));

        assertEquals(artifactIds.isEmpty() ? List.of() : List.of(artifactIds.split(" ")), artifactIds(results));
        assertEquals(4, results.getCount());
    }

    @Test
    void testLimitIsCappedAtOneThousand() {
        when(storage.getArtifactVersionContent(eq(GROUP), startsWith("candidate-"), eq("1")))
                .thenReturn(StoredArtifactVersionDto.builder().content(ContentHandle.create(COMPATIBLE_TOOL)).build());
        List<String> artifactIds = new ArrayList<>(List.of(SOURCE));
        IntStream.range(0, 1001).forEach(candidate -> artifactIds.add("candidate-" + candidate));
        registry(artifactIds);

        McpToolCompatibilityResults results = resource.getToolCompatibility(GROUP, SOURCE, null, null, FIRST,
                BigInteger.valueOf(Integer.MAX_VALUE), artifactIds.size());

        assertEquals(1001, results.getCount());
        assertEquals(1000, results.getResults().size());
    }

    @Test
    void testOffsetAndLimitWithoutValueTakeTheirDefaults() {
        when(storage.getArtifactVersionContent(eq(GROUP), startsWith("candidate-"), eq("1")))
                .thenReturn(StoredArtifactVersionDto.builder().content(ContentHandle.create(COMPATIBLE_TOOL)).build());
        List<String> artifactIds = new ArrayList<>(List.of(SOURCE));
        IntStream.range(0, 25).forEach(candidate -> artifactIds.add("candidate-" + candidate));
        registry(artifactIds);

        McpToolCompatibilityResults compatibility = compatibility(null, null, null);
        McpCompatibleToolsResults compatible = resource.findCompatibleTools(GROUP, SOURCE, null, null, null);

        assertEquals(25, compatibility.getCount());
        assertEquals(artifactIds.subList(1, 21), artifactIds(compatibility));
        assertEquals(25, compatible.getCount());
        assertEquals(artifactIds.subList(1, 21),
                compatible.getTools().stream().map(McpToolSearchResult::getArtifactId).toList());
    }

    @Test
    void testPaginationAppliesAfterTheVerdictFilter() {
        storeCatalog();

        McpToolCompatibilityResults results = compatibility(List.of("INDETERMINATE"), BigInteger.ONE, BigInteger.ONE);

        assertEquals(List.of("garbled"), artifactIds(results));
        assertEquals(2, results.getCount());
    }

    /**
     * The first case overflowed into a 500 before (#9908), and a limit of 0 returned one tool.
     */
    @ParameterizedTest
    @CsvSource({
            "1, 2147483647, second third",
            "0, 0, ''",
            "-1, -1, first",
            "0, 100000000000000000000, first second third",
            "100000000000000000000, 20, ''",
    })
    void testCompatiblePaginationIsNormalized(String offset, String limit, String artifactIds) {
        storeTool("first", COMPATIBLE_TOOL);
        storeTool("second", COMPATIBLE_TOOL);
        storeTool("third", COMPATIBLE_TOOL);
        scan("first", "second", "third");

        McpCompatibleToolsResults results = resource.findCompatibleTools(GROUP, SOURCE, null,
                new BigInteger(offset), new BigInteger(limit));

        assertEquals(3, results.getCount());
        assertEquals(artifactIds.isEmpty() ? List.of() : List.of(artifactIds.split(" ")),
                results.getTools().stream().map(McpToolSearchResult::getArtifactId).toList());
    }

    static Stream<Arguments> sourcesThatCannotBeCompared() {
        return Stream.of(
                Arguments.of("{\"name\": \"source\", \"inputSchema\": {\"type\": \"object\"}}",
                        limitation("SOURCE_HAS_NO_OUTPUT_SCHEMA", McpToolCompatibilitySide.PRODUCER,
                                "/outputSchema", "The tool declares no outputSchema")),
                Arguments.of("""
                        {"name": "source", "inputSchema": {"type": "object"},
                         "outputSchema": {"$schema": "https://example.com/schema", "type": "object"}}
                        """,
                        limitation("UNSUPPORTED_DIALECT", McpToolCompatibilitySide.PRODUCER,
                                "/outputSchema/$schema",
                                "JSON Schema dialect 'https://example.com/schema' is not supported")),
                Arguments.of("{not json", limitation("COMPARISON_FAILED", McpToolCompatibilitySide.PRODUCER,
                        "", "The tool definition is not valid JSON")));
    }

    @ParameterizedTest
    @MethodSource("sourcesThatCannotBeCompared")
    void testSourceThatCannotBeComparedIsExplainedWithoutAScan(String source,
            McpToolCompatibilityLimitation limitation) {
        storeTool(SOURCE, source);

        McpToolCompatibilityResults results = compatibility(null, FIRST, PAGE);

        assertEquals(McpToolCompatibilityResults.builder()
                .candidateTotal(0)
                .evaluated(0)
                .scanLimit(500)
                .complete(true)
                .totals(totals(0, 0, 0))
                .count(0)
                .limitations(List.of(limitation))
                .results(List.of())
                .build(), results);
        verify(storage, never()).searchArtifacts(anySet(), any(), any(), anyInt(), anyInt(), eq(false));
    }

    /**
     * The scan limit is 3. The count of the search includes the source, so the scan is complete
     * exactly when every other tool is among the ones it returned, wherever the source is.
     */
    @ParameterizedTest(name = "{0} tools, source at position {1}")
    @CsvSource({
            "2, 0, 1, true",
            "2, 1, 1, true",
            "3, 0, 2, true",
            "3, 2, 2, true",
            "4, 0, 2, false",
            "4, 2, 2, false",
            "4, 3, 3, true",
            "5, 4, 3, false",
    })
    void testCompleteReportsWhetherEveryToolOtherThanTheSourceWasScanned(int tools, int sourcePosition,
            int evaluated, boolean complete) {
        List<String> artifactIds = new ArrayList<>();
        for (int candidate = 0; candidate < tools - 1; candidate++) {
            storeTool("candidate-" + candidate, COMPATIBLE_TOOL);
            artifactIds.add("candidate-" + candidate);
        }
        artifactIds.add(sourcePosition, SOURCE);
        registry(artifactIds);

        McpToolCompatibilityResults results = resource.getToolCompatibility(GROUP, SOURCE, null, null, FIRST,
                PAGE, SCAN_LIMIT);

        assertEquals(tools, results.getCandidateTotal());
        assertEquals(evaluated, results.getEvaluated());
        assertEquals(SCAN_LIMIT, results.getScanLimit());
        assertEquals(complete, results.getComplete());
    }

    @Test
    void testSkippedCandidateIsScannedButNotEvaluated() {
        storeTool("compatible", COMPATIBLE_TOOL);
        when(storage.getArtifactVersionContent(GROUP, "deleted", "1"))
                .thenThrow(new ArtifactNotFoundException(GROUP, "deleted"));
        registry(List.of(SOURCE, "deleted", "compatible"));

        McpToolCompatibilityResults results = resource.getToolCompatibility(GROUP, SOURCE, null, null, FIRST,
                PAGE, SCAN_LIMIT);

        assertEquals(List.of("compatible"), artifactIds(results));
        assertEquals(1, results.getEvaluated());
        assertTrue(results.getComplete());
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
        return resource.findCompatibleTools(GROUP, SOURCE, null, FIRST, PAGE).getTools().stream()
                .map(McpToolSearchResult::getArtifactId)
                .toList();
    }

    private McpToolCompatibilityResults compatibility(List<String> verdicts, BigInteger offset, BigInteger limit) {
        return resource.getToolCompatibility(GROUP, SOURCE, null, verdicts, offset, limit);
    }

    /**
     * Stores one candidate for each verdict, plus one whose content is not JSON, newest first.
     */
    private void storeCatalog() {
        storeTool("compatible", COMPATIBLE_TOOL);
        storeTool("incompatible", INCOMPATIBLE_TOOL);
        storeTool("indeterminate", INDETERMINATE_TOOL);
        storeTool("garbled", "{not json");
        scan("compatible", "incompatible", "indeterminate", "garbled");
    }

    /**
     * Registers the given MCP tools, newest first, for a search that honours its limit and counts
     * every tool.
     */
    private void registry(List<String> artifactIds) {
        List<SearchedArtifactDto> tools = artifactIds.stream()
                .map(artifactId -> SearchedArtifactDto.builder().groupId(GROUP).artifactId(artifactId)
                        .name(artifactId).createdOn(new Date()).build())
                .toList();
        when(storage.searchArtifacts(anySet(), eq(OrderBy.createdOn), eq(OrderDirection.desc), eq(0),
                anyInt(), eq(false)))
                .thenAnswer(invocation -> ArtifactSearchResultsDto.builder()
                        .artifacts(tools.subList(0, Math.min(invocation.<Integer>getArgument(4), tools.size())))
                        .count(tools.size())
                        .build());
    }

    private static List<String> artifactIds(McpToolCompatibilityResults results) {
        return results.getResults().stream().map(result -> result.getTool().getArtifactId()).toList();
    }

    private static McpToolCompatibilityLimitation limitation(String code, McpToolCompatibilitySide side,
            String pointer, String message) {
        return McpToolCompatibilityLimitation.builder().code(code).side(side).pointer(pointer).message(message)
                .build();
    }

    private static McpToolCompatibilityTotals totals(int compatible, int incompatible, int indeterminate) {
        return McpToolCompatibilityTotals.builder().compatible(compatible).incompatible(incompatible)
                .indeterminate(indeterminate).build();
    }
}
