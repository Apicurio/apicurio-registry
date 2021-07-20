/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Map;

@RegisterForReflection
public class PartlyFilledArtifactMetaDataDto extends EditableArtifactMetaDataDto {

    /**
     * Constructor.
     */
    public PartlyFilledArtifactMetaDataDto(String name, String description, List<String> labels, Map<String, String> properties) {
        super(name, description, labels, properties);
    }

    /**
     * Constructor.
     */
    public PartlyFilledArtifactMetaDataDto() {
        super();
    }

}
