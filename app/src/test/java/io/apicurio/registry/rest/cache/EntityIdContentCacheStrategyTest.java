package io.apicurio.registry.rest.cache;

import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.cache.strategy.EntityIdContentCacheStrategy;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.types.ReferenceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static io.apicurio.registry.rest.cache.Cacheability.HIGH;
import static io.apicurio.registry.rest.cache.Cacheability.LOW;
import static io.apicurio.registry.rest.cache.Cacheability.MODERATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EntityIdContentCacheStrategy} cacheability logic.
 * Uses mockStatic on {@link HttpCaching#getBeanOrNull} to control CDI bean resolution.
 */
class EntityIdContentCacheStrategyTest {

    private MockedStatic<HttpCaching> httpCachingMock;
    private HttpCachingConfig cachingConfig;
    private RestConfig restConfig;

    @BeforeEach
    void setUp() {
        cachingConfig = mock(HttpCachingConfig.class);
        restConfig = mock(RestConfig.class);

        httpCachingMock = mockStatic(HttpCaching.class, invocation -> {
            // Let non-mocked methods call through (not applicable for static-only mock)
            return null;
        });
        httpCachingMock.when(() -> HttpCaching.getBeanOrNull(eq(HttpCachingConfig.class))).thenReturn(cachingConfig);
        httpCachingMock.when(() -> HttpCaching.getBeanOrNull(eq(RestConfig.class))).thenReturn(restConfig);

        // Default: both features enabled
        when(cachingConfig.isHigherQualityETagsEnabled()).thenReturn(true);
        when(restConfig.isArtifactVersionMutabilityEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        httpCachingMock.close();
    }

    @Test
    void entityIdOnly_highCacheability() {
        var strategy = EntityIdContentCacheStrategy.builder()
                .entityId(42L)
                .build();
        strategy.evaluate();

        assertEquals(HIGH, strategy.getCacheability());
        assertEquals("entityId=42", strategy.getETagBuilder().build().getValue());
    }

    @Test
    void entityIdNull_throws() {
        var strategy = EntityIdContentCacheStrategy.builder().build();
        assertThrows(NullPointerException.class, strategy::evaluate);
    }

    @Test
    void preserveReferences_staysHigh() {
        var strategy = EntityIdContentCacheStrategy.builder()
                .entityId(42L)
                .references(HandleReferencesType.PRESERVE)
                .build();
        strategy.evaluate();

        assertEquals(HIGH, strategy.getCacheability());
    }

    @Test
    void dereferenceWithHigherQualityEtags_moderate() {
        var strategy = EntityIdContentCacheStrategy.builder()
                .entityId(42L)
                .references(HandleReferencesType.DEREFERENCE)
                .referenceTreeContentIds(() -> List.of(10L, 20L))
                .build();
        strategy.evaluate();

        assertEquals(MODERATE, strategy.getCacheability());
        assertNotNull(strategy.getETagBuilder().build().getValue());
    }

    @Test
    void dereferenceWithoutHigherQualityEtags_low() {
        when(cachingConfig.isHigherQualityETagsEnabled()).thenReturn(false);

        var strategy = EntityIdContentCacheStrategy.builder()
                .entityId(42L)
                .references(HandleReferencesType.DEREFERENCE)
                .referenceTreeContentIds(() -> List.of(10L, 20L))
                .build();
        strategy.evaluate();

        assertEquals(LOW, strategy.getCacheability());
    }

    @Test
    void dereferenceWithoutReferenceTreeIds_low() {
        var strategy = EntityIdContentCacheStrategy.builder()
                .entityId(42L)
                .references(HandleReferencesType.DEREFERENCE)
                .build();
        strategy.evaluate();

        assertEquals(LOW, strategy.getCacheability());
    }

    @Test
    void dereferenceWithMutabilityDisabled_staysHigh() {
        when(restConfig.isArtifactVersionMutabilityEnabled()).thenReturn(false);

        var strategy = EntityIdContentCacheStrategy.builder()
                .entityId(42L)
                .references(HandleReferencesType.DEREFERENCE)
                .build();
        strategy.evaluate();

        assertEquals(HIGH, strategy.getCacheability());
    }

    @Test
    void inboundReferences_low() {
        var strategy = EntityIdContentCacheStrategy.builder()
                .entityId(42L)
                .refType(ReferenceType.INBOUND)
                .build();
        strategy.evaluate();

        assertEquals(LOW, strategy.getCacheability());
    }

    @Test
    void outboundReferences_staysHigh() {
        var strategy = EntityIdContentCacheStrategy.builder()
                .entityId(42L)
                .refType(ReferenceType.OUTBOUND)
                .build();
        strategy.evaluate();

        assertEquals(HIGH, strategy.getCacheability());
    }

    @Test
    void returnArtifactTypeIncludedInEtag() {
        var s1 = EntityIdContentCacheStrategy.builder()
                .entityId(42L).returnArtifactType(true).build();
        s1.evaluate();
        var s2 = EntityIdContentCacheStrategy.builder()
                .entityId(42L).returnArtifactType(false).build();
        s2.evaluate();

        assertEquals(HIGH, s1.getCacheability());
        assertEquals(HIGH, s2.getCacheability());
        assertTrue(s1.getETagBuilder().build().getValue().contains("returnArtifactType=true"));
        assertTrue(s2.getETagBuilder().build().getValue().contains("returnArtifactType=false"));
    }
}
