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
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@RegisterForReflection
@Getter
@Setter
@ToString
public class UpgraderKey implements MessageKey {

    /**
     * All upgrader messages must end up in the same partition to maintain absolute ordering.
     */
    private static final String PARTITION_KEY = "__apicurio_registry_upgrader__";

    /**
     * This is a shared "pseudo-UUID" that is used for messages that can be compacted.
     */
    private static final String COMPACTING_UUID = "compact";

    private String UUID;

    public static UpgraderKey create(boolean allowCompacting) {
        UpgraderKey key = new UpgraderKey();
        if (allowCompacting) {
            key.setUUID(COMPACTING_UUID);
        } else {
            key.setUUID(java.util.UUID.randomUUID().toString());
        }
        return key;
    }


    @Override
    public MessageType getType() {
        return MessageType.Upgrader;
    }


    @Override
    public String getPartitionKey() {
        return PARTITION_KEY;
    }


    @Override
    public String getTenantId() {
        return null;
    }
}
