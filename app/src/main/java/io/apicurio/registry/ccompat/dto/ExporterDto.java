package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;

@JsonAutoDetect(isGetterVisibility = NONE)
@NoArgsConstructor // required for Jackson
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class ExporterDto {

    @JsonProperty("name")
    private String name;

    @JsonProperty("contextType")
    private String contextType;

    @JsonProperty("context")
    private String context;

    @JsonProperty("subjects")
    private List<String> subjects;

    @JsonProperty("subjectRenameFormat")
    private String subjectRenameFormat;

    @JsonProperty("config")
    private Map<String, String> config;
}
