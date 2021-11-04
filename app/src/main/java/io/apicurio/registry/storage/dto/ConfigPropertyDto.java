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

package io.apicurio.registry.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @author eric.wittmann@gmail.com
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ConfigPropertyDto {

    public static <T> ConfigPropertyDto create(String name, T value) {
        return ConfigPropertyDto.builder().name(name).type(value.getClass().getName()).value(value.toString()).build();
    }

    private String name;
    private String type;
    private String value;

    // TODO move this logic to the registry config service instead?  lets the storage only deal with strings
    public Object getTypedValue() {
        if (value == null) {
            return null;
        }

        // TODO use the full canonical class name instead?
        if ("String".equals(type)) {
            return value;
        }
        if ("Boolean".equals(type)) {
            return "true".equals(value);
        }
        if ("Integer".equals(type)) {
            return Integer.valueOf(value);
        }
        if ("Long".equals(type)) {
            return Long.valueOf(value);
        }
        throw new UnsupportedOperationException("Configuration property type not supported: " + type + " for property with name: " + name);
    }

}
