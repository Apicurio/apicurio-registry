package io.apicurio.registry.types.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.Set;

import io.apicurio.registry.avro.content.AvroContentAccepter;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.types.ContentTypes;

/**
 * Unit test for {@link ProviderConfig} and {@link ProviderConfig.Builder}.
 * <p>
 * Verifies fail-fast validation rules (non-empty contentTypes, non-null suppliers),
 * default component fallbacks, and defensive unmodifiability of configured content types.
 */
class ProviderConfigTest {

    @Test
    void testBuild_rejectsEmptyContentTypes() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderConfig.Builder().build());
    }

    @Test
    void testBuild_rejectsNullSupplier() {
        assertThrows(NullPointerException.class, () -> new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .accepter(null)
                .build());
    }

    @Test
    void testBuild_acceptsMinimalConfig() {
        assertDoesNotThrow(() -> new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .accepter(AvroContentAccepter::new)
                .build());
    }

    @Test
    void testBuild_defaultCompatibilityCheckerIsNoop() {
        ProviderConfig config = new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .build();
        assertInstanceOf(NoopCompatibilityChecker.class, config.getCompatibilityChecker().get());
    }

    @Test
    void testBuild_contentTypesIsUnmodifiable() {
        ProviderConfig config = new ProviderConfig.Builder()
                .contentTypes(Set.of(ContentTypes.APPLICATION_JSON))
                .build();
        assertThrows(UnsupportedOperationException.class, () -> config.getContentTypes().add("application/xml"));
    }

}
