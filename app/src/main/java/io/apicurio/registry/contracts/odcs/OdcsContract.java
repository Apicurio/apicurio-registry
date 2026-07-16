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
    /**
     * Schema artifacts governed by this contract. Entries are independent: each projects only onto
     * its own {@code location}. Nested types that live in other registry artifacts need their own
     * entry (or a separate contract); projection does not follow cross-artifact schema references.
     */
    private List<OdcsSchema> schemas;
    private OdcsQuality quality;
    private OdcsServiceLevel serviceLevel;
    private OdcsTeam team;
    private Map<String, Object> terms;
    private List<Map<String, Object>> roles;
    private List<Map<String, Object>> servers;
    private Map<String, Object> links;
    private List<String> tags;
    private List<Map<String, Object>> customProperties;
    private List<Map<String, Object>> support;
    private Map<String, Object> price;
    private List<Map<String, Object>> slaProperties;
    private List<Map<String, Object>> authoritativeDefinitions;
    private String tenant;
    private String dataProduct;
    private String contractCreatedTs;
    private String domain;
    private String version;
    private String status;
    private Object description;

    private List<Map<String, Object>> schema;

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
