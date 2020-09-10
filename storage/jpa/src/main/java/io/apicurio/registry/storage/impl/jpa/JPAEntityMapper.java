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

package io.apicurio.registry.storage.impl.jpa;

import javax.enterprise.context.Dependent;

import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.impl.jpa.entity.Artifact;
import io.apicurio.registry.content.ContentHandle;

@Dependent
public class JPAEntityMapper {

    public StoredArtifact toStoredArtifact(Artifact artifact) {
        return StoredArtifact.builder()
                .globalId(artifact.getGlobalId())
                .version(artifact.getVersion())
                .content(ContentHandle.create(artifact.getContent()))
                .build();
    }
}
