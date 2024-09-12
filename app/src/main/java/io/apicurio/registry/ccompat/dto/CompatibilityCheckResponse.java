package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
public class CompatibilityCheckResponse {

    public static final CompatibilityCheckResponse IS_COMPATIBLE = new CompatibilityCheckResponse(true, null);

    public static final CompatibilityCheckResponse IS_NOT_COMPATIBLE = new CompatibilityCheckResponse(false,
            null);

    public static CompatibilityCheckResponse create(boolean isCompatible) {
        return isCompatible ? IS_COMPATIBLE : IS_NOT_COMPATIBLE;
    }

    @JsonProperty("is_compatible")
    private boolean isCompatible;

    @JsonProperty("reason")
    private String reason;
}
