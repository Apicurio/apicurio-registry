package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.apicurio.registry.ccompat.SchemaTypeFilter;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;


@JsonAutoDetect(isGetterVisibility = NONE)
@NoArgsConstructor // required for Jackson
@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class Schema {

    @JsonProperty("id")
    private int id;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("version")
    private int version;

    @JsonProperty("schema")
    private String schema;

    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SchemaTypeFilter.class)
    @JsonProperty("schemaType")
    private String schemaType;

    @JsonProperty("references")
    private List<SchemaReference> references;
}
