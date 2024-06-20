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
        BACKWARD("BACKWARD"), BACKWARD_TRANSITIVE("BACKWARD_TRANSITIVE"), FORWARD(
                "FORWARD"), FORWARD_TRANSITIVE(
                        "FORWARD_TRANSITIVE"), FULL("FULL"), FULL_TRANSITIVE("FULL_TRANSITIVE"), NONE("NONE");

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
