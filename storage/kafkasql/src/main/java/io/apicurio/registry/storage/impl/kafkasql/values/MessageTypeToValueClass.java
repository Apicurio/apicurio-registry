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

package io.apicurio.registry.storage.impl.kafkasql.values;

import java.util.HashMap;
import java.util.Map;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;

/**
 * Provides a mapping from a message type to the {@link MessageValue} for that message type.
 * @author eric.wittmann@gmail.com
 */
public class MessageTypeToValueClass {

    private static final MessageType[] types = MessageType.values();
    private static final Map<MessageType, Class<? extends MessageValue>> index = new HashMap<>();
    static {
        for (MessageType type : types) {
            switch (type) {
                case Bootstrap:
                    break;
                case Group:
                    index.put(type, GroupValue.class);
                    break;
                case Artifact:
                    index.put(type, ArtifactValue.class);
                    break;
                case ArtifactRule:
                    index.put(type, ArtifactRuleValue.class);
                    break;
                case Content:
                    index.put(type, ContentValue.class);
                    break;
                case GlobalRule:
                    index.put(type, GlobalRuleValue.class);
                    break;
                case LogConfig:
                    index.put(type, LogConfigValue.class);
                    break;
                case ArtifactVersion:
                    index.put(type, ArtifactVersionValue.class);
                    break;
                case GlobalId:
                    index.put(type, GlobalIdValue.class);
                    break;
                case ContentId:
                    index.put(type, ContentIdValue.class);
                    break;
                case RoleMapping:
                    index.put(type, RoleMappingValue.class);
                    break;
                case GlobalAction:
                    index.put(type, GlobalActionValue.class);
                    break;
                case Download:
                    index.put(type, DownloadValue.class);
                    break;
                case ConfigProperty:
                    index.put(type, ConfigPropertyValue.class);
                    break;
                case ArtifactOwner:
                    index.put(type, ArtifactOwnerValue.class);
                    break;
                default:
                    throw new RuntimeException("[MessageTypeToValueClass] Type not mapped: " + type);
            }
        }
    }

    public static final Class<? extends MessageValue> typeToValue(MessageType type) {
        return index.get(type);
    }

    public static final Class<? extends MessageValue> ordToValue(byte typeOrdinal) {
        MessageType type = MessageType.fromOrd(typeOrdinal);
        return typeToValue(type);
    }

}
