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

package io.apicurio.registry.storage.impl.kafkasql.v2compat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;

/**
 * Simplified version from the v2 KafkaSQL implementation.
 */
@RegisterForReflection
@Getter
public class UpgraderKey implements MessageKey {

    // For some reason, the message key JSON contained lowercase 'uuid' key instead of 'UUID' during testing,
    // even thought the v2 code uses 'UUID'. We'll check both then.
    private String uuid;

    private String UUID;

    @Override
    public MessageType getType() {
        return MessageType.Upgrader;
    }

    public String getEitherUUID() {
        return UUID != null ? UUID : uuid;
    }
}
