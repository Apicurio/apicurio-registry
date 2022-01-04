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

package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.CustomRuleType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
@ToString
public class CustomRuleValue extends AbstractMessageValue {

    private CustomRuleType customRuleType;
    private ArtifactType supportedArtifactType;
    private String description;
    private String config;

    /**
     * Creator method.
     * @param action
     * @param config
     */
    public static final CustomRuleValue create(ActionType action, String config, String description, CustomRuleType customRuleType, ArtifactType supportedArtifactType) {
        CustomRuleValue value = new CustomRuleValue();
        value.setAction(action);
        value.config = config;
        value.description = description;
        value.customRuleType = customRuleType;
        value.supportedArtifactType = supportedArtifactType;
        return value;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.values.MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.CustomRule;
    }

    /**
     * @return the customRuleType
     */
    public CustomRuleType getCustomRuleType() {
        return customRuleType;
    }

    /**
     * @return the supportedArtifactType
     */
    public ArtifactType getSupportedArtifactType() {
        return supportedArtifactType;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the config
     */
    public String getConfig() {
        return config;
    }

}
