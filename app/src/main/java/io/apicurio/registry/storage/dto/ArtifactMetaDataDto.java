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

import io.apicurio.registry.types.ArtifactState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;


@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ArtifactMetaDataDto {

    private String groupId;
    private String id;
    private String name;
    private String description;
    private String createdBy;
    private long createdOn;
    private String modifiedBy;
    private long modifiedOn;
    private String version;
    private int versionId;
    private long globalId;
    private long contentId;
    private String type;
    private ArtifactState state;
    private List<String> labels;
    private Map<String, String> properties;
    private List<ArtifactReferenceDto> references;

    public ArtifactMetaDataDto() {
    }
}
