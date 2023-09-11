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

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import lombok.Getter;

import java.util.Map;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public class GitTestRepositoryManager implements QuarkusTestResourceLifecycleManager {

    @Getter
    private static GitTestRepository testRepository;


    @Override
    public Map<String, String> start() {
        testRepository = new GitTestRepository();
        testRepository.initialize();

        return Map.of(
                "registry.gitops.id", "test",
                "registry.gitops.repo.origin.uri", testRepository.getGitRepoUrl(),
                "registry.gitops.repo.origin.branch", testRepository.getGitRepoBranch(),
                "registry.gitops.refresh.every", "5s"
        );
    }


    @Override
    public void stop() {
        try {
            testRepository.close();
            testRepository = null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
