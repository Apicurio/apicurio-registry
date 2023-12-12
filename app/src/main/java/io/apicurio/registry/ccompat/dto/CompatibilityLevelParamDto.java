package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;

/**
 *
 */
@JsonAutoDetect(isGetterVisibility = NONE)
@NoArgsConstructor // required for Jackson
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class CompatibilityLevelParamDto {

    private String compatibilityLevel;
}
