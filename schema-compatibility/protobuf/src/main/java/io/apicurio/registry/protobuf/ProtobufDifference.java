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

package io.apicurio.registry.protobuf;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * @author Fabian Martinez
 */
@EqualsAndHashCode
@ToString
public class ProtobufDifference {

    private final String message;

    public static ProtobufDifference from(String message) {
        return new ProtobufDifference(message);
    }

    public ProtobufDifference(String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }

}
