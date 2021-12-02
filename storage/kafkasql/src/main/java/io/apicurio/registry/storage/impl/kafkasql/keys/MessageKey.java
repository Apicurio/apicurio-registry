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

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * When the KSQL artifactStore publishes a message to its Kafka topic, the message key will be a class that
 * implements this interface.
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
public interface MessageKey {

    /**
     * Returns the message type.
     */
    @JsonIgnore
    public MessageType getType();

    /**
     * Returns the key that should be used when partitioning the messages.
     */
    @JsonIgnore
    public String getPartitionKey();

    /**
     * Returns the tenantId for this key.
     */
    public String getTenantId();
}
