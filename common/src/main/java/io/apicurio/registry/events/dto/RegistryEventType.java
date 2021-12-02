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
package io.apicurio.registry.events.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author Fabian Martinez
 */
@RegisterForReflection
public enum RegistryEventType {

    GROUP_CREATED,
    GROUP_UPDATED,
    GROUP_DELETED,

    ARTIFACTS_IN_GROUP_DELETED,

    ARTIFACT_CREATED,
    ARTIFACT_UPDATED,
    ARTIFACT_DELETED,

    ARTIFACT_STATE_CHANGED,

    ARTIFACT_RULE_CREATED,
    ARTIFACT_RULE_UPDATED,
    ARTIFACT_RULE_DELETED,
    ALL_ARTIFACT_RULES_DELETED,

    GLOBAL_RULE_CREATED,
    GLOBAL_RULE_UPDATED,
    GLOBAL_RULE_DELETED,
    ALL_GLOBAL_RULES_DELETED;

    private String cloudEventType;

    private RegistryEventType() {
        this.cloudEventType = "io.apicurio.registry."+this.name().toLowerCase().replace("_", "-");
    }

    public String cloudEventType() {
        return this.cloudEventType;
    }
}
