/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage.impl.gitops;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.GitopsTestProfile;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;


@QuarkusTest
@TestProfile(GitopsTestProfile.class)
class GitOpsSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    @Current
    RegistryStorage storage;


    @Test
    void smokeTest() throws Exception {
        assertEquals(Set.of(), storage.getArtifactIds(10));

        var testRepository = GitTestRepositoryManager.getTestRepository();

        // Waiting to load smoke01
        testRepository.load("git/smoke01");
        await().atMost(Duration.ofSeconds(30))
                .until(() -> withContext(() -> storage.getArtifactIds(10)), equalTo(Set.of("petstore")));

        // Global rules
        assertEquals(Set.of(RuleType.VALIDITY), Set.copyOf(storage.getGlobalRules()));
        assertEquals("FULL", storage.getGlobalRule(RuleType.VALIDITY).getConfiguration());

        // Groups
        assertEquals(Set.of("foo"), Set.copyOf(storage.getGroupIds(10)));

        // Artifact rules
        assertEquals(Set.of(RuleType.COMPATIBILITY), Set.copyOf(storage.getArtifactRules("foo", "petstore")));
        assertEquals("BACKWARD", storage.getArtifactRule("foo", "petstore", RuleType.COMPATIBILITY).getConfiguration());

        // Artifact versions
        var version = storage.getArtifactVersion("foo", "petstore", "1");
        assertEquals(1, version.getGlobalId());
        assertEquals(1, version.getContentId());
        var content = loadFile("git/smoke01/content/petstore-1.0.0.yaml");
        assertEquals(YAMLObjectMapper.MAPPER.readTree(content.bytes()), MAPPER.readTree(version.getContent().bytes()));

        // Waiting to load smoke02
        testRepository.load("git/smoke02");
        await().atMost(Duration.ofSeconds(30))
                .until(() -> withContext(() -> storage.getArtifactIds(10)), equalTo(Set.of("person")));

        // Global rules
        assertEquals(Set.of(), Set.copyOf(storage.getGlobalRules()));

        // Groups
        assertEquals(Set.of("bar"), Set.copyOf(storage.getGroupIds(10)));

        // Artifact rules
        assertEquals(Set.of(), Set.copyOf(storage.getArtifactRules("bar", "person")));

        // Artifact versions
        version = storage.getArtifactVersion("bar", "person", "1");
        assertEquals(1, version.getGlobalId());
        assertEquals(42, version.getContentId());
        content = loadFile("git/smoke02/content/Person.json");
        assertEquals(MAPPER.readTree(content.bytes()), MAPPER.readTree(version.getContent().bytes()));

        // Waiting to load empty
        testRepository.load("git/empty");
        await().atMost(Duration.ofSeconds(30))
                .until(() -> withContext(() -> storage.getArtifactIds(10)), equalTo(Set.of()));
    }


    @ActivateRequestContext
    public <T> T withContext(Supplier<T> supplier) {
        return supplier.get();
    }


    private ContentHandle loadFile(String path) {
        try {
            var fullPath = Path.of(requireNonNull(Thread.currentThread().getContextClassLoader().getResource(path)).toURI());
            return ContentHandle.create(FileUtils.readFileToByteArray(fullPath.toFile()));
        } catch (IOException | URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }
}
