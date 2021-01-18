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

package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.keys.MessageType;

/**
 * @author Fabian Martinez
 */
public class LoggingConfigurationValue extends AbstractMessageValue {

    private String logLevel;

    /**
     * Creator method.
     * @param action
     * @param config
     */
    public static final LoggingConfigurationValue create(ActionType action, String logLevel) {
        LoggingConfigurationValue key = new LoggingConfigurationValue();
        key.setAction(action);
        key.setLogLevel(logLevel);
        return key;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.values.MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.LoggingConfig;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

}
