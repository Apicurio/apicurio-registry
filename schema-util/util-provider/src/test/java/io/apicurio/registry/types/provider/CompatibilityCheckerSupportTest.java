package io.apicurio.registry.types.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.apicurio.registry.types.ArtifactType;

/**
 * Guards the boundary between artifact types that can really evaluate a COMPATIBILITY rule and those whose
 * checker is a stub.
 * <p>
 * A stub checker answers "compatible" without comparing anything, so a type that silently falls back to one
 * lets a breaking change through while the registry reports success. The set below is therefore pinned:
 * adding a new artifact type without a compatibility checker, or dropping an existing checker, fails this
 * test and forces the decision to be made deliberately.
 * </p>
 */
class CompatibilityCheckerSupportTest {

    /**
     * Artifact types that knowingly ship without a compatibility checker implementation. Enforcing a
     * non-NONE compatibility level on these is rejected at rule-execution time rather than silently passing.
     */
    private static final Set<String> TYPES_WITHOUT_COMPATIBILITY_SUPPORT = new TreeSet<>(Set.of(
            ArtifactType.ASYNCAPI,
            ArtifactType.GRAPHQL,
            ArtifactType.KCONNECT,
            ArtifactType.ODCS_CONTRACT,
            ArtifactType.OPENRPC,
            ArtifactType.THRIFT,
            ArtifactType.WSDL,
            ArtifactType.XML));

    @Test
    void testUnsupportedTypesAreExactlyTheKnownSet() {
        Set<String> actual = StandardArtifactTypeProviderRegistry.createStandardProviders().stream()
                .filter(provider -> !provider.getCompatibilityChecker().isCompatibilitySupported())
                .map(ArtifactTypeUtilProvider::getArtifactType)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(TYPES_WITHOUT_COMPATIBILITY_SUPPORT, actual,
                "The set of artifact types without a compatibility checker changed. If a checker was added,"
                        + " remove the type from TYPES_WITHOUT_COMPATIBILITY_SUPPORT. If a new type was added"
                        + " without one, either implement a checker or add it here deliberately -- it will"
                        + " reject non-NONE COMPATIBILITY rules at runtime.");
    }

    @Test
    void testRemainingTypesReportCompatibilitySupported() {
        List<ArtifactTypeUtilProvider> providers = StandardArtifactTypeProviderRegistry
                .createStandardProviders();

        for (ArtifactTypeUtilProvider provider : providers) {
            boolean supported = provider.getCompatibilityChecker().isCompatibilitySupported();
            if (TYPES_WITHOUT_COMPATIBILITY_SUPPORT.contains(provider.getArtifactType())) {
                assertFalse(supported, provider.getArtifactType()
                        + " is listed as unsupported but reports compatibility support");
            } else {
                assertTrue(supported, provider.getArtifactType()
                        + " has a real compatibility checker but reports it is unsupported");
            }
        }
    }
}
