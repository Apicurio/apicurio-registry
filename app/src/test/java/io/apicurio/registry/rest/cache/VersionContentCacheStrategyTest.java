package io.apicurio.registry.rest.cache;

import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.cache.strategy.VersionContentCacheStrategy;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.types.VersionState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static io.apicurio.registry.rest.cache.Cacheability.HIGH;
import static io.apicurio.registry.rest.cache.Cacheability.LOW;
import static io.apicurio.registry.rest.cache.Cacheability.MODERATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class VersionContentCacheStrategyTest {

    private MockedStatic<HttpCaching> httpCachingMock;
    private HttpCachingConfig cachingConfig;
    private RestConfig restConfig;

    @BeforeEach
    void setUp() {
        cachingConfig = mock(HttpCachingConfig.class);
        restConfig = mock(RestConfig.class);

        httpCachingMock = mockStatic(HttpCaching.class);
        httpCachingMock.when(() -> HttpCaching.getBeanOrNull(eq(HttpCachingConfig.class))).thenReturn(cachingConfig);
        httpCachingMock.when(() -> HttpCaching.getBeanOrNull(eq(RestConfig.class))).thenReturn(restConfig);

        when(cachingConfig.isHigherQualityETagsEnabled()).thenReturn(true);
        when(restConfig.isArtifactVersionMutabilityEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        httpCachingMock.close();
    }

    @Test
    void concreteVersion_enabled_high() {
        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("1.0.0")
                .contentId(42L)
                .versionState(VersionState.ENABLED)
                .build();
        strategy.evaluate();

        assertEquals(HIGH, strategy.getCacheability());
        assertTrue(strategy.getETagBuilder().build().getValue().contains("contentId=42"));
    }

    @Test
    void versionExpressionRequired() {
        var strategy = VersionContentCacheStrategy.builder().build();
        assertThrows(NullPointerException.class, strategy::evaluate);
    }

    @Test
    void missingContentId_low() {
        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("1.0.0")
                .versionState(VersionState.ENABLED)
                .build();
        strategy.evaluate();

        assertEquals(LOW, strategy.getCacheability());
    }

    @Test
    void missingVersionState_low() {
        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("1.0.0")
                .contentId(42L)
                .build();
        strategy.evaluate();

        assertEquals(LOW, strategy.getCacheability());
    }

    @Test
    void draftVersion_moderate() {
        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("1.0.0")
                .contentId(42L)
                .versionState(VersionState.DRAFT)
                .build();
        strategy.evaluate();

        assertEquals(MODERATE, strategy.getCacheability());
    }

    @Test
    void draftWithMutabilityDisabled_staysHigh() {
        when(restConfig.isArtifactVersionMutabilityEnabled()).thenReturn(false);

        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("1.0.0")
                .contentId(42L)
                .versionState(VersionState.DRAFT)
                .build();
        strategy.evaluate();

        assertEquals(HIGH, strategy.getCacheability());
    }

    @Test
    void branchExpression_moderate() {
        // "branch=main" contains '=' so VersionId.isValid() returns false
        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("branch=main")
                .contentId(42L)
                .versionState(VersionState.ENABLED)
                .build();
        strategy.evaluate();

        assertEquals(MODERATE, strategy.getCacheability());
    }

    @Test
    void concreteVersionNamedLatest_treatedAsConcrete() {
        // "latest" matches VersionId pattern [a-zA-Z0-9._\-+], so it's treated as a concrete
        // version ID (a version literally named "latest"), NOT as a "get latest version" expression.
        // To get the latest version, users use "branch=latest" which fails VersionId.isValid()
        // and is handled as a version expression with MODERATE cacheability.
        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("latest")
                .contentId(42L)
                .versionState(VersionState.ENABLED)
                .build();
        strategy.evaluate();

        assertEquals(HIGH, strategy.getCacheability());
    }

    @Test
    void deprecatedVersion_staysHigh() {
        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("1.0.0")
                .contentId(42L)
                .versionState(VersionState.DEPRECATED)
                .build();
        strategy.evaluate();

        assertEquals(HIGH, strategy.getCacheability());
    }

    @Test
    void dereferenceReferences_moderate() {
        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("1.0.0")
                .contentId(42L)
                .versionState(VersionState.ENABLED)
                .references(HandleReferencesType.DEREFERENCE)
                .referenceTreeContentIds(() -> List.of(10L, 20L))
                .build();
        strategy.evaluate();

        assertEquals(MODERATE, strategy.getCacheability());
    }

    @Test
    void inboundReferences_low() {
        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("1.0.0")
                .contentId(42L)
                .versionState(VersionState.ENABLED)
                .refType(ReferenceType.INBOUND)
                .build();
        strategy.evaluate();

        assertEquals(LOW, strategy.getCacheability());
    }

    @Test
    void multipleDegradingFactors_takesLowest() {
        // branch expression + DRAFT + INBOUND -> min(MODERATE, MODERATE, LOW) = LOW
        var strategy = VersionContentCacheStrategy.builder()
                .versionExpression("branch=main")
                .contentId(42L)
                .versionState(VersionState.DRAFT)
                .refType(ReferenceType.INBOUND)
                .build();
        strategy.evaluate();

        assertEquals(LOW, strategy.getCacheability());
    }
}
