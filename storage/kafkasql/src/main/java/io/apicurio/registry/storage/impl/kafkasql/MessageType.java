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

package io.apicurio.registry.storage.impl.kafkasql;

import java.util.HashMap;
import java.util.Map;

/**
 * @author eric.wittmann@gmail.com
 */
public enum MessageType {

    Bootstrap(0),
    GlobalRule(1),
    Content(2),
    Artifact(3),
    ArtifactRule(4),
    ArtifactVersion(5),
    Group(6),
    LogConfig(7),
    GlobalId(8),
    ContentId(9),
    RoleMapping(10),
    GlobalAction(11),
    Download(12),
    ConfigProperty(13),
    ArtifactOwner(14),
    ;

    private final byte ord;

    /**
     * Constructor.
     */
    private MessageType(int ord) {
        this.ord = (byte) ord;
    }

    public final byte getOrd() {
        return this.ord;
    }

    private static final Map<Byte, MessageType> ordIndex = new HashMap<>();
    static {
        for (MessageType mt : MessageType.values()) {
            if (ordIndex.containsKey(mt.getOrd())) {
                throw new IllegalArgumentException(String.format("Duplicate ord value %d for MessageType %s", mt.getOrd(), mt.name()));
            }
            ordIndex.put(mt.getOrd(), mt);
        }
    }
    public static final MessageType fromOrd(byte ord) {
        return ordIndex.get(ord);
    }

}
