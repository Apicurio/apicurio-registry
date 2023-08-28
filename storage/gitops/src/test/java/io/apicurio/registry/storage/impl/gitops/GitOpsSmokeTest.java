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

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@QuarkusTest
@QuarkusTestResource(GitTestRepositoryManager.class)
class GitOpsSmokeTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    void simpleTest() {

        assertEquals(Set.of(), storage.getArtifactIds(10));

        var testRepository = GitTestRepositoryManager.getTestRepository();
        testRepository.load("git/smoke");

        await().atMost(Duration.ofSeconds(30))
                .until(() -> storage.getArtifactIds(10), Matchers.equalTo(Set.of("petstore")));
    }
}
