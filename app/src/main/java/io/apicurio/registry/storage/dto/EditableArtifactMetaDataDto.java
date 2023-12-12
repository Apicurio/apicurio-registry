package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class EditableArtifactMetaDataDto {

    private String name;
    private String description;
    private List<String> labels;
    private Map<String, String> properties;

    /**
     * Constructor.
     */
    public EditableArtifactMetaDataDto() {
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the labels
     */
    public List<String> getLabels() {
        return labels;
    }

    /**
     * @param labels the labels to set
     */
    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    /**
     * @return the user-defined properties
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * @param properties the user-defined properties to set
     */
    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
}
