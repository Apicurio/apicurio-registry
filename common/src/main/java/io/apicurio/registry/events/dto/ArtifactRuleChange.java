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

package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author Fabian Martinez
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArtifactRuleChange extends ArtifactId {

    @JsonProperty("rule")
    private String rule;

    /**
     * @return the rule
     */
    @JsonProperty("rule")
    public String getRule() {
        return rule;
    }

    /**
     * @param rule the rule to set
     */
    @JsonProperty("rule")
    public void setRule(String rule) {
        this.rule = rule;
    }

}
