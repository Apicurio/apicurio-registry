package io.apicurio.registry.types.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.apicurio.registry.avro.content.canon.EnhancedAvroContentCanonicalizer;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.NoOpContentAccepter;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.extract.NoopContentExtractor;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.iceberg.content.IcebergContentValidator;
import io.apicurio.registry.protobuf.content.extract.ProtobufStructuredContentExtractor;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.xsd.rules.compatibility.XsdCompatibilityChecker;

/**
 * Unit test for {@link StandardArtifactTypeProviderRegistry} and {@link ConfigurableArtifactTypeUtilProvider}.
 * <p>
 * Verifies that the registry produces the expected 15 core provider instances in deterministic order,
 * resolves custom component suppliers correctly, and integrates cleanly with {@link DefaultArtifactTypeUtilProviderImpl}.
 */
class StandardArtifactTypeProviderRegistryTest {

    private static final List<String> EXPECTED_PROVIDER_ORDER = List.of(
            ArtifactType.PROTOBUF,
            ArtifactType.OPENAPI,
            ArtifactType.ASYNCAPI,
            ArtifactType.JSON,
            ArtifactType.AVRO,
            ArtifactType.GRAPHQL,
            ArtifactType.KCONNECT,
            ArtifactType.WSDL,
            ArtifactType.XSD,
            ArtifactType.XML,
            ArtifactType.ICEBERG_TABLE,
            ArtifactType.ICEBERG_VIEW,
            ArtifactType.OPENRPC,
            ArtifactType.ODCS_CONTRACT,
            ArtifactType.THRIFT
    );

    @Test
    void testCreateStandardProviders_count() {
        List<ArtifactTypeUtilProvider> providers = StandardArtifactTypeProviderRegistry.createStandardProviders();
        assertEquals(15, providers.size());
    }

    @Test
    void testCreateStandardProviders_order() {
        List<ArtifactTypeUtilProvider> providers = StandardArtifactTypeProviderRegistry.createStandardProviders();
        List<String> types = providers.stream().map(ArtifactTypeUtilProvider::getArtifactType).collect(Collectors.toList());
        assertEquals(EXPECTED_PROVIDER_ORDER, types);
    }

    @Test
    void testCreateStandardProviders_allInstancesAreConfigurable() {
        List<ArtifactTypeUtilProvider> providers = StandardArtifactTypeProviderRegistry.createStandardProviders();
        for (ArtifactTypeUtilProvider provider : providers) {
            assertInstanceOf(ConfigurableArtifactTypeUtilProvider.class, provider);
        }
    }

    @Test
    void testDefaultArtifactTypeUtilProviderImpl_lookup() {
        DefaultArtifactTypeUtilProviderImpl factory = new DefaultArtifactTypeUtilProviderImpl(true);
        assertEquals(EXPECTED_PROVIDER_ORDER, factory.getAllArtifactTypes());
        assertInstanceOf(ConfigurableArtifactTypeUtilProvider.class,
                factory.getArtifactTypeProvider(ArtifactType.AVRO));
    }

    @Test
    void testDefaultArtifactTypeUtilProviderImpl_unknownType() {
        DefaultArtifactTypeUtilProviderImpl factory = new DefaultArtifactTypeUtilProviderImpl(true);
        assertThrows(IllegalStateException.class, () -> factory.getArtifactTypeProvider("GARBAGE"));
    }

    @Test
    void testSupportsReferencesWithContext() {
        List<ArtifactTypeUtilProvider> providers = StandardArtifactTypeProviderRegistry.createStandardProviders();
        assertTrue(findProvider(providers, ArtifactType.JSON).supportsReferencesWithContext());
        assertFalse(findProvider(providers, ArtifactType.AVRO).supportsReferencesWithContext());
        assertTrue(findProvider(providers, ArtifactType.OPENRPC).supportsReferencesWithContext());
        assertFalse(findProvider(providers, ArtifactType.PROTOBUF).supportsReferencesWithContext());
    }

    @Test
    void testAvroCanonicalizer() {
        ArtifactTypeUtilProvider avro = findProvider(
                StandardArtifactTypeProviderRegistry.createStandardProviders(), ArtifactType.AVRO);
        ContentCanonicalizer canonicalizer = avro.getContentCanonicalizer();
        assertInstanceOf(EnhancedAvroContentCanonicalizer.class, canonicalizer);
    }

    @Test
    void testProtobufStructuredContentExtractor() {
        ArtifactTypeUtilProvider protobuf = findProvider(
                StandardArtifactTypeProviderRegistry.createStandardProviders(), ArtifactType.PROTOBUF);
        StructuredContentExtractor extractor = protobuf.getStructuredContentExtractor();
        assertInstanceOf(ProtobufStructuredContentExtractor.class, extractor);
    }

    @Test
    void testKConnectUsesNoopDefaults() {
        ArtifactTypeUtilProvider kconnect = findProvider(
                StandardArtifactTypeProviderRegistry.createStandardProviders(), ArtifactType.KCONNECT);
        ContentAccepter accepter = kconnect.getContentAccepter();
        assertSame(NoOpContentAccepter.INSTANCE, accepter);
        assertInstanceOf(NoopContentExtractor.class, kconnect.getContentExtractor());
    }

    @Test
    void testOpenRpcContentTypes() {
        ArtifactTypeUtilProvider openRpc = findProvider(
                StandardArtifactTypeProviderRegistry.createStandardProviders(), ArtifactType.OPENRPC);
        assertEquals(
                Set.of(ContentTypes.APPLICATION_JSON, ContentTypes.APPLICATION_YAML),
                openRpc.getContentTypes());
    }

    @Test
    void testOpenRpcUsesNoopCompatibilityChecker() {
        ArtifactTypeUtilProvider openRpc = findProvider(
                StandardArtifactTypeProviderRegistry.createStandardProviders(), ArtifactType.OPENRPC);
        assertInstanceOf(NoopCompatibilityChecker.class, openRpc.getCompatibilityChecker());
    }

    @Test
    void testXsdCompatibilityChecker() {
        ArtifactTypeUtilProvider xsd = findProvider(
                StandardArtifactTypeProviderRegistry.createStandardProviders(), ArtifactType.XSD);
        assertInstanceOf(XsdCompatibilityChecker.class, xsd.getCompatibilityChecker());
    }

    @Test
    void testIcebergTableAndViewValidators() {
        List<ArtifactTypeUtilProvider> providers = StandardArtifactTypeProviderRegistry.createStandardProviders();
        IcebergContentValidator tableValidator = (IcebergContentValidator) findProvider(providers,
                ArtifactType.ICEBERG_TABLE).getContentValidator();
        IcebergContentValidator viewValidator = (IcebergContentValidator) findProvider(providers,
                ArtifactType.ICEBERG_VIEW).getContentValidator();
        assertInstanceOf(IcebergContentValidator.class, tableValidator);
        assertInstanceOf(IcebergContentValidator.class, viewValidator);
        assertNotSame(tableValidator, viewValidator);
    }

    @Test
    void testMergeContributions_rejectsTypeAlreadyInCore() {
        Map<String, ProviderConfig> core = Map.of(ArtifactType.AVRO, emptyConfig());
        ArtifactTypeProviderContributor contributor = () -> Map.of(ArtifactType.AVRO, emptyConfig());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StandardArtifactTypeProviderRegistry.mergeContributions(core, List.of(contributor)));
        assertEquals("Artifact type provider registered more than once: AVRO", ex.getMessage());
    }

    @Test
    void testMergeContributions_rejectsTypeContributedTwice() {
        ArtifactTypeProviderContributor first = () -> Map.of("CUSTOM", emptyConfig());
        ArtifactTypeProviderContributor second = () -> Map.of("CUSTOM", emptyConfig());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> StandardArtifactTypeProviderRegistry.mergeContributions(Map.of(), List.of(first, second)));
        assertEquals("Artifact type provider registered more than once: CUSTOM", ex.getMessage());
    }

    @Test
    void testMergeContributions_keepsContributedConfigs() {
        ProviderConfig config = emptyConfig();
        ArtifactTypeProviderContributor contributor = () -> Map.of("CUSTOM", config);
        Map<String, ProviderConfig> merged = StandardArtifactTypeProviderRegistry.mergeContributions(
                Map.of(ArtifactType.AVRO, emptyConfig()), List.of(contributor));
        assertEquals(Set.of("CUSTOM"), merged.keySet());
        assertSame(config, merged.get("CUSTOM"));
    }

    @Test
    void testOrderTypes_contributedTypesTakeTheirDetectionSlot() {
        Set<String> types = new LinkedHashSet<>(List.of(ArtifactType.THRIFT, "CUSTOM", ArtifactType.MCP_TOOL,
                ArtifactType.XML, ArtifactType.ICEBERG_TABLE, ArtifactType.AGENT_CARD));
        assertEquals(List.of(ArtifactType.XML, ArtifactType.AGENT_CARD, ArtifactType.MCP_TOOL,
                ArtifactType.ICEBERG_TABLE, ArtifactType.THRIFT, "CUSTOM"),
                StandardArtifactTypeProviderRegistry.orderTypes(types));
    }

    private static ProviderConfig emptyConfig() {
        return new ProviderConfig.Builder().contentTypes(Set.of(ContentTypes.APPLICATION_JSON)).build();
    }

    private static ArtifactTypeUtilProvider findProvider(List<ArtifactTypeUtilProvider> providers, String type) {
        return providers.stream()
                .filter(p -> type.equals(p.getArtifactType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No provider for type: " + type));
    }

}
