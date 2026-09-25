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

package io.apicurio.registry.storage.impl.kafkasql.v2compat;


import io.apicurio.registry.exception.UnreachableCodeException;

import java.util.HashMap;
import java.util.Map;

/**
 * Simplified version from the v2 KafkaSQL implementation.
 */
public class MessageTypeToKeyClass {

    private static final MessageType[] types = MessageType.values();
    private static final Map<MessageType, Class<? extends MessageKey>> index = new HashMap<>();

    static {
        for (MessageType type : types) {
            switch (type) {
                case Bootstrap:
                    index.put(type, BootstrapKey.class);
                    break;
                case Upgrader:
                    index.put(type, UpgraderKey.class);
                    break;
                default:
                    throw new UnreachableCodeException();
            }
        }
    }

    public static Class<? extends MessageKey> typeToKey(MessageType type) {
        return index.get(type);
    }

    public static Class<? extends MessageKey> ordToKeyClass(byte typeOrdinal) {
        MessageType type = MessageType.fromOrd(typeOrdinal);
        return typeToKey(type);
    }
}
