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

package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonValue;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;

/**
 * Immutable.
 *
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@JsonAutoDetect(isGetterVisibility = NONE)
@NoArgsConstructor // required for Jackson
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class CompatibilityLevelDto {

    public static CompatibilityLevelDto create(Optional<CompatibilityLevel> source) {
        return new CompatibilityLevelDto(Level.create(source));
    }

    private Level compatibility;

    public enum Level {
        BACKWARD("BACKWARD"),
        BACKWARD_TRANSITIVE("BACKWARD_TRANSITIVE"),
        FORWARD("FORWARD"),
        FORWARD_TRANSITIVE("FORWARD_TRANSITIVE"),
        FULL("FULL"),
        FULL_TRANSITIVE("FULL_TRANSITIVE"),
        NONE("NONE");

        public static Level create(Optional<CompatibilityLevel> source) {
            return source.map(c -> {
                switch (c) {
                    case BACKWARD:
                        return Level.BACKWARD;
                    case BACKWARD_TRANSITIVE:
                        return Level.BACKWARD_TRANSITIVE;
                    case FORWARD:
                        return Level.FORWARD;
                    case FORWARD_TRANSITIVE:
                        return Level.FORWARD_TRANSITIVE;
                    case FULL:
                        return Level.FULL;
                    case FULL_TRANSITIVE:
                        return Level.FULL_TRANSITIVE;
                    case NONE:
                        return Level.NONE;
                }
                return null;
            }).orElse(Level.NONE);
        }

        @Getter
        @JsonValue
        private final String stringValue;

        Level(String level) {
            this.stringValue = level;
        }
    }
}
