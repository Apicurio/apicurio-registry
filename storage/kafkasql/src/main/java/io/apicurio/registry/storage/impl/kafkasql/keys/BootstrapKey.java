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
public class BootstrapKey implements MessageKey {

    private String bootstrapId;

    /**
     * Creator method.
     * @param bootstrapId
     */
    public static final BootstrapKey create(String bootstrapId) {
        BootstrapKey key = new BootstrapKey();
        key.setBootstrapId(bootstrapId);
        return key;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Bootstrap;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return "__bootstrap_message__";
    }

    /**
     * @return the bootstrapId
     */
    public String getBootstrapId() {
        return bootstrapId;
    }

    /**
     * @param bootstrapId the bootstrapId to set
     */
    public void setBootstrapId(String bootstrapId) {
        this.bootstrapId = bootstrapId;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getTenantId()
     */
    @Override
    public String getTenantId() {
        return null;
    }

}
