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

package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
public class CustomRuleKey extends AbstractMessageKey {

    /**
     * Sharing partition key between customrules and customrulebindings to ensure custom rules are created before any customrulebinding can be created
     */
    public static final String ALL_CUSTOM_RULESPARTITION_KEY = "__apicurio_registry_customrules__";

    private String customRuleId;

    /**
     * Creator method.
     * @param tenantId
     * @param ruleType
     */
    public static final CustomRuleKey create(String tenantId, String customRuleId) {
        CustomRuleKey key = new CustomRuleKey();
        key.setTenantId(tenantId);
        key.customRuleId = customRuleId;
        return key;
    }

    /**
     * @return the customRuleId
     */
    public String getCustomRuleId() {
        return customRuleId;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.CustomRule;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return getTenantId() + ALL_CUSTOM_RULESPARTITION_KEY;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.AbstractMessageKey#toString()
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[customRuleId=" + this.customRuleId + "]";
    }

}
