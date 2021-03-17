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

import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;

/**
 * @author eric.wittmann@gmail.com
 */
public class ArtifactRuleValue extends AbstractMessageValue {

    private RuleConfigurationDto config;

    /**
     * Creator method.
     * @param action
     * @param config
     */
    public static final ArtifactRuleValue create(ActionType action, RuleConfigurationDto config) {
        ArtifactRuleValue value = new ArtifactRuleValue();
        value.setAction(action);
        value.setConfig(config);
        return value;
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.values.MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.ArtifactRule;
    }

    /**
     * @return the config
     */
    public RuleConfigurationDto getConfig() {
        return config;
    }

    /**
     * @param config the config to set
     */
    public void setConfig(RuleConfigurationDto config) {
        this.config = config;
    }
    
}
