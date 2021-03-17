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

/**
 * @author eric.wittmann@gmail.com
 */
public class LogConfigKey extends AbstractMessageKey {

    private static final String LOG_CONFIG_PARTITION_KEY = "__apicurio_registry_log_config__";

    /**
     * Creator method.
     * @param tenantId
     */
    public static final LogConfigKey create(String tenantId) {
        LogConfigKey key = new LogConfigKey();
        key.setTenantId(tenantId);
        return key;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.LogConfig;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return getTenantId() + LOG_CONFIG_PARTITION_KEY;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "LogConfigKey []";
    }

}
