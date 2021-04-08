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

package io.apicurio.registry.storage.impl.kafkasql.keys;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;

/**
 * @author eric.wittmann@gmail.com
 */
public class GlobalIdKey extends AbstractMessageKey {

    private static final String GLOBAL_ID_PARTITION_KEY = "__apicurio_registry_global_id__";

    /**
     * Creator method.
     * @param tenantId
     * @param ruleType
     */
    public static final GlobalIdKey create() {
        GlobalIdKey key = new GlobalIdKey();
        return key;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.GlobalId;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return getTenantId() + GLOBAL_ID_PARTITION_KEY;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "GlobalIdKey []";
    }

}
