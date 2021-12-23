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

package io.apicurio.registry.storage.dto;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.CustomRuleType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * @author Fabian Martinez
 */
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class CustomRuleDto {

    private CustomRuleType customRuleType;

    private ArtifactType supportedArtifactType;

    private String id;
    private String description;

    private String config; //json

    public CustomRuleDto() {
        super();
    }

    /**
     * @return the customRuleType
     */
    public CustomRuleType getCustomRuleType() {
        return customRuleType;
    }

    /**
     * @param customRuleType the customRuleType to set
     */
    public void setCustomRuleType(CustomRuleType customRuleType) {
        this.customRuleType = customRuleType;
    }

    /**
     * @return the supportedArtifactType
     */
    public ArtifactType getSupportedArtifactType() {
        return supportedArtifactType;
    }

    /**
     * @param supportedArtifactType the supportedArtifactType to set
     */
    public void setSupportedArtifactType(ArtifactType supportedArtifactType) {
        this.supportedArtifactType = supportedArtifactType;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the config
     */
    public String getConfig() {
        return config;
    }

    /**
     * @param config the config to set
     */
    public void setConfig(String config) {
        this.config = config;
    }

}
