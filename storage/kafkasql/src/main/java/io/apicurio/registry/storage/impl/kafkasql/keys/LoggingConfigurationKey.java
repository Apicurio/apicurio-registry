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

/**
 *
 * @author Fabian Martinez
 *
 */
public class LoggingConfigurationKey extends AbstractMessageKey {

    private static final String LOGGING_CONFIG_PARTITION_KEY = "__apicurio_registry_logging_configuration__";

    private String logger;

    /**
     * Creator method.
     * @param ruleType
     */
    public static final LoggingConfigurationKey create(String logger) {
        LoggingConfigurationKey key = new LoggingConfigurationKey();
        key.setLogger(logger);
        return key;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.LoggingConfig;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return LOGGING_CONFIG_PARTITION_KEY;
    }

    public String getLogger() {
        return logger;
    }

    public void setLogger(String logger) {
        this.logger = logger;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.AbstractMessageKey#toString()
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[logger=" + getLogger() + "]";
    }
}
