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

package io.apicurio.registry.storage;

import io.apicurio.registry.content.ContentHandle;
import lombok.Builder;
import lombok.Value;

/**
 * @author eric.wittmann@gmail.com
 */
@Value
@Builder
public class StoredArtifact { // TODO rename this to ArtifactVersion

    private Long id; // TODO Which ID is this?

    // TODO add artifactId

    private Long version;

    // TODO Can the CH be used multiple times?
    private ContentHandle content;

    //  Assuming global
    public Long getGlobalId() {
        return id;
    }
}
