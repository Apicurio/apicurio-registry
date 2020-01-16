/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An interface that is used to determine whether a proposed artifact's content is compatible
 * with older version(s) of the same content, based on a given compatibility level.
 * @author Ales Justin
 */
public interface ArtifactTypeAdapter {
    default boolean isCompatibleWith(CompatibilityLevel compatibilityLevel, List<ContentHandle> existingArtifacts, ContentHandle proposedArtifact) {
        return isCompatibleWith(
            compatibilityLevel,
            existingArtifacts.stream().map(ContentHandle::content).collect(Collectors.toList()),
            proposedArtifact.content()
        );
    }

    boolean isCompatibleWith(CompatibilityLevel compatibilityLevel, List<String> existingArtifacts, String proposedArtifact);
}
