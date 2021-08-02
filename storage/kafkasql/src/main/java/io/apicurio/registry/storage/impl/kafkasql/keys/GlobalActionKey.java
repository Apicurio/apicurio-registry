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

/**
 * Key that carries no additional information (tenantId excepted).
 * Does not apply to a specific resource, but to the entire node.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
@RegisterForReflection
public class GlobalActionKey extends AbstractMessageKey {

    private static final String EMPTY_PARTITION_KEY = "__apicurio_registry_global_action__";

    public static GlobalActionKey create(String tenantId) {
        GlobalActionKey key = new GlobalActionKey();
        key.setTenantId(tenantId);
        return key;
    }

    /**
     * @see MessageKey#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.GlobalAction;
    }

    /**
     * @see MessageKey#getPartitionKey()
     */
    @Override
    public String getPartitionKey() {
        return getTenantId() + EMPTY_PARTITION_KEY;
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return String.format("GlobalKey(super = %s)", super.toString());
    }
}
