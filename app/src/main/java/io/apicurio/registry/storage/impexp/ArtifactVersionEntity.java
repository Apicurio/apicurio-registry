/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.storage.impexp;

import java.util.List;
import java.util.Map;

import io.apicurio.registry.types.ArtifactState;

/**
 * @author eric.wittmann@gmail.com
 *
 * globalId BIGINT NOT NULL,
    groupId VARCHAR(512) NOT NULL,
    artifactId VARCHAR(512) NOT NULL,
    version VARCHAR(256),
    versionId INT NOT NULL,
    state VARCHAR(64) NOT NULL,
    name VARCHAR(512),
    description VARCHAR(1024),
    createdBy VARCHAR(256),
    createdOn TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    labels TEXT,
    properties TEXT,
    contentId BIGINT NOT NULL);
 */
public class ArtifactVersionEntity extends Entity {

    public long globalId;
    public String groupId;
    public String artifactId;
    public String version;
    public int versionId;
    public ArtifactState state;
    public String name;
    public String description;
    public String createdBy;
    public long createdOn;
    public List<String> labels;
    public Map<String, String> properties;

}
