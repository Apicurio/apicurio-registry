package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.apicurio.registry.ccompat.SchemaTypeFilter;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;

@JsonAutoDetect(isGetterVisibility = NONE)
@Getter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class SchemaInfo {

    @JsonProperty("schema")
    private String schema;

    @JsonProperty("schemaType")
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SchemaTypeFilter.class)
    private String schemaType;

    @JsonProperty("references")
    private List<SchemaReference> references;

    public SchemaInfo(String schema, String schemaType, List<SchemaReference> references) {
        this.schema = schema;
        this.schemaType = schemaType;
        this.references = references;
    }

    public SchemaInfo(String schema) {
        this.schema = schema;
    }

    public SchemaInfo() {
    }

}
