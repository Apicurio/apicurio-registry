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

/**
 * One schema entry from an ODCS contract ({@code schemas[]}).
 * <p>
 * Each entry is independent: field metadata (PII, tags, classification) is projected only onto the
 * artifact named in {@link #location}. Cross-artifact references inside the schema content are not
 * resolved during projection. To govern a nested/shared type that lives in another artifact, add a
 * separate {@code schemas[]} entry for that artifact or give it its own contract.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OdcsSchema {
    private String name;
    private String type;
    /**
     * Registry location in the form {@code [groupId/]artifactId[:versionOrBranch]}.
     * Group may be omitted (defaults to the contract's group). Version/branch expression is optional
     * (defaults to the latest version).
     */
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
