package io.apicurio.registry.contracts.odcs;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OdcsSchema {
    private String name;
    private String type;
    private String location;
    private Map<String, OdcsFieldMetadata> fields;

    @Builder.Default
    private Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String key, Object value) {
        if (additionalProperties == null) {
            additionalProperties = new LinkedHashMap<>();
        }
        additionalProperties.put(key, value);
    }
}
