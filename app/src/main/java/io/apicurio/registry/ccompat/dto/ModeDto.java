package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;

@JsonAutoDetect(isGetterVisibility = NONE)
@NoArgsConstructor // required for Jackson
@Getter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class ModeDto {

    /**
     * Mode values supported by the Confluent Schema Registry API.
     */
    public enum Mode {
        READWRITE, READONLY, IMPORT
    }

    @JsonProperty("mode")
    private String mode;

    public ModeDto(String mode) {
        this.mode = mode;
    }

    public ModeDto(Mode mode) {
        this.mode = mode.name();
    }

    public Mode getModeEnum() {
        return Mode.valueOf(mode);
    }
}
