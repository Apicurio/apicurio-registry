/*
 * Copyright 2023 Red Hat
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
package io.apicurio.registry.resolver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.resolver.strategy.ArtifactCoordinates;

public class ERCacheTest {

    @Test
    void testCheckInitializedPassesWithContentHashKeyExtractor() {
        ERCache<String> cache = newCache("foo key");

        cache.checkInitialized();
    }

    @Test
    void testCheckInitializedFailsWithoutContentHashKeyExtractor() {
        ERCache<Object> cache = new ERCache<>();
        Function<Object, Long> globalIdKeyExtractor = (o) -> {return 1L;};
        Function<Object, Long> contentIdKeyExtractor = (o) -> {return 2L;};
        Function<Object, ArtifactCoordinates> artifactKeyExtractor = (o) -> {return ArtifactCoordinates.builder().artifactId("artifact id").build();};
        Function<Object, String> contentKeyExtractor = (o) -> {return "content";};

        cache.configureGlobalIdKeyExtractor(globalIdKeyExtractor);
        cache.configureContentIdKeyExtractor(contentIdKeyExtractor);
        cache.configureArtifactCoordinatesKeyExtractor(artifactKeyExtractor);
        cache.configureContentKeyExtractor(contentKeyExtractor);

        assertThrows(IllegalStateException.class, () -> {cache.checkInitialized();});
    }

    @Test
    void testContainsByContentHash() {
        String contentHashKey = "some key";
        ERCache<String> cache = newCache(contentHashKey);
        Function<String, String> staticValueLoader = (key) -> {return "present";};

        assertFalse(cache.containsByContentHash(contentHashKey));
        cache.getByContentHash(contentHashKey, staticValueLoader);

        assertTrue(cache.containsByContentHash(contentHashKey));
    }

    @Test
    void testGetByContentHash() {
        String contentHashKey = "content hash key";
        ERCache<String> cache = newCache(contentHashKey);
        Function<String, String> staticValueLoader = (key) -> {return "value";};
        Function<String, String> ensureCachedLoader = (key) -> {throw new IllegalStateException("this should've been cached");};

        String uncachedValue = cache.getByContentHash(contentHashKey, staticValueLoader);
        assertEquals("value", uncachedValue);
        assertDoesNotThrow(() -> {
            String cachedValue = cache.getByContentHash(contentHashKey, ensureCachedLoader);
            assertEquals("value", cachedValue);
        });
    }

    @Test
    void testGetByContentHashEnforcesTTL() {
        String contentHashKey = "content hash ttl key";
        ERCache<String> cache = newCache(contentHashKey);
        cache.configureLifetime(Duration.ZERO);
        Function<String, String> firstLoader = (key) -> {return "a value";};
        Function<String, String> secondLoader = (key) -> {return "another value";};

        String firstValue = cache.getByContentHash(contentHashKey, firstLoader);
        assertEquals("a value", firstValue);
        String secondValue = cache.getByContentHash(contentHashKey, secondLoader);
        assertEquals("another value", secondValue);
    }

    @Test
    void testClearEmptiesContentHashIndex() {
        String contentHashKey = "another key";
        ERCache<String> cache = newCache(contentHashKey);
        Function<String, String> staticValueLoader = (key) -> {return "some value";};
        cache.getByContentHash(contentHashKey, staticValueLoader);

        cache.clear();

        assertFalse(cache.containsByContentHash(contentHashKey));
    }

    private ERCache<String> newCache(String contentHashKey) {
        ERCache<String> cache = new ERCache<>();
        cache.configureLifetime(Duration.ofDays(30));
        Function<String, Long> globalIdKeyExtractor = (o) -> {return 1L;};
        Function<String, Long> contentIdKeyExtractor = (o) -> {return 2L;};
        Function<String, String> contentHashKeyExtractor = (o) -> {return contentHashKey;};
        Function<String, ArtifactCoordinates> artifactKeyExtractor = (o) -> {return ArtifactCoordinates.builder().artifactId("artifact id").build();};
        Function<String, String> contentKeyExtractor = (o) -> {return "content";};

        cache.configureGlobalIdKeyExtractor(globalIdKeyExtractor);
        cache.configureContentIdKeyExtractor(contentIdKeyExtractor);
        cache.configureContentHashKeyExtractor(contentHashKeyExtractor);
        cache.configureArtifactCoordinatesKeyExtractor(artifactKeyExtractor);
        cache.configureContentKeyExtractor(contentKeyExtractor);

        return cache;
    }
}
