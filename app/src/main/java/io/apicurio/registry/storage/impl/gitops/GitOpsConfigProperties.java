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

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;


@ApplicationScoped
public class GitOpsConfigProperties {

    @ConfigProperty(name = "registry.gitops.id")
    @Info(category = "gitops", description = "Identifier of this Registry instance. Only data that references this identifier " +
            "will be loaded.", availableSince = "3.0.0")
    @Getter
    String registryId;

    @ConfigProperty(name = "registry.gitops.workdir", defaultValue = "/tmp/apicurio-registry-gitops")
    @Info(category = "gitops", description = "Path to GitOps working directory, which is used to store the local git repository.", availableSince = "3.0.0")
    @Getter
    String workDir;

    @ConfigProperty(name = "registry.gitops.repo.origin.uri")
    @Info(category = "gitops", description = "URI of the remote git repository containing data to be loaded.", availableSince = "3.0.0")
    @Getter
    String originRepoURI;

    @ConfigProperty(name = "registry.gitops.repo.origin.branch", defaultValue = "main")
    @Info(category = "gitops", description = "Name of the branch in the remote git repository containing data to be loaded.", availableSince = "3.0.0")
    @Getter
    String originRepoBranch;
}
