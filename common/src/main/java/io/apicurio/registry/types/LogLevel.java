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
package io.apicurio.registry.types;

import java.util.EnumSet;
import java.util.Map;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * @author Fabian Martinez
 */
public enum LogLevel {

    //Compatibility with SLFJ
    DEBUG("DEBUG"),
    TRACE("TRACE"),

    //levels from java.util.logging
    SEVERE("SEVERE"),
    WARNING("WARNING"),
    INFO("INFO"),
    CONFIG("CONFIG"),
    FINE("FINE"),
    FINER("FINER"),
    FINEST("FINEST");

    private final String value;
    private final static Map<String, LogLevel> CONSTANTS = EnumSet.allOf(LogLevel.class).stream().collect(Collectors.toMap(ll -> ll.value, ll -> ll));

    private LogLevel(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    @JsonValue
    public String value() {
        return this.value;
    }

    @JsonCreator
    public static LogLevel fromValue(String value) {
        LogLevel constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }

}
