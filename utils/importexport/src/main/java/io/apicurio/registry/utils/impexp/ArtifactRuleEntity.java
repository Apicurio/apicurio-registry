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

package io.apicurio.registry.utils.impexp;

import io.apicurio.registry.types.RuleType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
public class ArtifactRuleEntity extends Entity {

    public String groupId;
    public String artifactId;
    public RuleType type;
    public String configuration;

    /**
     * @see io.apicurio.registry.utils.impexp.Entity#getEntityType()
     */
    @Override
    public EntityType getEntityType() {
        return EntityType.ArtifactRule;
    }
}
