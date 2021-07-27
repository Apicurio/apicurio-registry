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

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
public enum ActionType {

    CREATE(1),
    UPDATE(2),
    DELETE(3),
    CLEAR(4),
    IMPORT(5),
    RESET(6),
    /**
     * Deletes ALL user (tenant) data. Does not delete global data, such as log configuration.
     */
    DELETE_ALL_USER_DATA(7);

    private final byte ord;

    /**
     * Constructor.
     */
    private ActionType(int ord) {
        this.ord = (byte) ord;
    }

    public final byte getOrd() {
        return this.ord;
    }

    private static final Map<Byte, ActionType> ordIndex = new HashMap<>();
    static {
        for (ActionType at : ActionType.values()) {
            if (ordIndex.containsKey(at.getOrd())) {
                throw new IllegalArgumentException(String.format("Duplicate ord value %d for ActionType %s", at.getOrd(), at.name()));
            }
            ordIndex.put(at.getOrd(), at);
        }
    }

    public static final ActionType fromOrd(byte ord) {
        return ordIndex.get(ord);
    }
}
