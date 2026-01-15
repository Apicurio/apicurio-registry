/*
 * Copyright 2024 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.langchain4j;

import io.apicurio.registry.rest.client.RegistryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ApicurioPromptRegistry.
 * <p>
 * Note: These tests focus on the registry's cache management and configuration behavior.
 * Full integration tests require a running registry server.
 */
@ExtendWith(MockitoExtension.class)
class ApicurioPromptRegistryTest {

    @Mock
    private RegistryClient client;

    @Mock
    private ApicurioRegistryConfig config;

    private ApicurioPromptRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ApicurioPromptRegistry();
        // Use reflection to inject mocks since we're not using Quarkus CDI in tests
        try {
            var clientField = ApicurioPromptRegistry.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(registry, client);

            var configField = ApicurioPromptRegistry.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(registry, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }

        lenient().when(config.defaultGroup()).thenReturn("default");
    }

    @Test
    void testGetPromptWithDefaultGroup() {
        // Arrange
        when(config.cacheEnabled()).thenReturn(false); // Disable cache so we get a fresh instance

        // Act
        ApicurioPromptTemplate template = registry.getPrompt("test-prompt");

        // Assert
        assertThat(template).isNotNull();
        assertThat(template.getGroupId()).isEqualTo("default");
        assertThat(template.getArtifactId()).isEqualTo("test-prompt");
        assertThat(template.getVersion()).isNull(); // Latest version
    }

    @Test
    void testGetPromptWithVersion() {
        // Arrange
        when(config.cacheEnabled()).thenReturn(false);

        // Act
        ApicurioPromptTemplate template = registry.getPrompt("test-prompt", "2.0");

        // Assert
        assertThat(template).isNotNull();
        assertThat(template.getGroupId()).isEqualTo("default");
        assertThat(template.getArtifactId()).isEqualTo("test-prompt");
        assertThat(template.getVersion()).isEqualTo("2.0");
    }

    @Test
    void testGetPromptFullyQualified() {
        // Arrange
        when(config.cacheEnabled()).thenReturn(false);

        // Act
        ApicurioPromptTemplate template = registry.getPrompt("custom-group", "custom-prompt", "3.0");

        // Assert
        assertThat(template).isNotNull();
        assertThat(template.getGroupId()).isEqualTo("custom-group");
        assertThat(template.getArtifactId()).isEqualTo("custom-prompt");
        assertThat(template.getVersion()).isEqualTo("3.0");
    }

    @Test
    void testCachingEnabled() {
        // Arrange
        when(config.cacheEnabled()).thenReturn(true);

        // Act - get same template twice
        ApicurioPromptTemplate template1 = registry.getPrompt("cached-prompt", "1.0");
        ApicurioPromptTemplate template2 = registry.getPrompt("cached-prompt", "1.0");

        // Assert - same instance returned (from cache)
        assertThat(template1).isSameAs(template2);
    }

    @Test
    void testCachingDisabled() {
        // Arrange
        when(config.cacheEnabled()).thenReturn(false);

        // Act - get same template twice
        ApicurioPromptTemplate template1 = registry.getPrompt("uncached-prompt", "1.0");
        ApicurioPromptTemplate template2 = registry.getPrompt("uncached-prompt", "1.0");

        // Assert - different instances (no caching)
        assertThat(template1).isNotSameAs(template2);
    }

    @Test
    void testCacheKeyIsolation() {
        // Arrange
        when(config.cacheEnabled()).thenReturn(true);

        // Act - get templates with different parameters
        ApicurioPromptTemplate template1 = registry.getPrompt("prompt", "1.0");
        ApicurioPromptTemplate template2 = registry.getPrompt("prompt", "2.0");
        ApicurioPromptTemplate template3 = registry.getPrompt("other-group", "prompt", "1.0");

        // Assert - different cache keys yield different instances
        assertThat(template1).isNotSameAs(template2);
        assertThat(template1).isNotSameAs(template3);
        assertThat(template2).isNotSameAs(template3);
    }

    @Test
    void testClearCache() {
        // Arrange
        when(config.cacheEnabled()).thenReturn(true);
        ApicurioPromptTemplate template1 = registry.getPrompt("clear-test", "1.0");

        // Act
        registry.clearCache();
        ApicurioPromptTemplate template2 = registry.getPrompt("clear-test", "1.0");

        // Assert - different instance after cache clear
        assertThat(template1).isNotSameAs(template2);
    }

    @Test
    void testEvictByArtifactId() {
        // Arrange
        when(config.cacheEnabled()).thenReturn(true);
        ApicurioPromptTemplate template1a = registry.getPrompt("evict-test-a", "1.0");
        ApicurioPromptTemplate template1b = registry.getPrompt("evict-test-b", "1.0");

        // Act - evict only prompt 'a'
        registry.evict("evict-test-a");
        ApicurioPromptTemplate template2a = registry.getPrompt("evict-test-a", "1.0");
        ApicurioPromptTemplate template2b = registry.getPrompt("evict-test-b", "1.0");

        // Assert
        assertThat(template1a).isNotSameAs(template2a); // Was evicted
        assertThat(template1b).isSameAs(template2b);     // Still cached
    }

    @Test
    void testEvictByGroupArtifactVersion() {
        // Arrange
        when(config.cacheEnabled()).thenReturn(true);
        ApicurioPromptTemplate template1v1 = registry.getPrompt("evict-version-test", "1.0");
        ApicurioPromptTemplate template1v2 = registry.getPrompt("evict-version-test", "2.0");

        // Act - evict only version 1.0
        registry.evict("default", "evict-version-test", "1.0");
        ApicurioPromptTemplate template2v1 = registry.getPrompt("evict-version-test", "1.0");
        ApicurioPromptTemplate template2v2 = registry.getPrompt("evict-version-test", "2.0");

        // Assert
        assertThat(template1v1).isNotSameAs(template2v1); // Was evicted
        assertThat(template1v2).isSameAs(template2v2);     // Still cached
    }

    @Test
    void testLatestVersionCacheKey() {
        // Arrange
        when(config.cacheEnabled()).thenReturn(true);

        // Act - null version and explicit null should use same cache key
        ApicurioPromptTemplate template1 = registry.getPrompt("latest-test");
        ApicurioPromptTemplate template2 = registry.getPrompt("latest-test", null);

        // Assert - same cache key for latest version
        assertThat(template1).isSameAs(template2);
    }
}
