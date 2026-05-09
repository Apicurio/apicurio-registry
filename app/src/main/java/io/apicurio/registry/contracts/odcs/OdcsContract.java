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
import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OdcsContract {
    private String apiVersion;
    private String kind;
    private String id;
    private OdcsInfo info;
    private List<OdcsSchema> schemas;
    private OdcsQuality quality;
    private OdcsServiceLevel serviceLevel;
    private OdcsTeam team;

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
