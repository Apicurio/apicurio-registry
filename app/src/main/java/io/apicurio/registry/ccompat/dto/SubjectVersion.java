package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;


@JsonAutoDetect(isGetterVisibility = NONE)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@Builder
@RegisterForReflection
public class SubjectVersion {

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("version")
    private Long version; // TODO How is this used?
}
