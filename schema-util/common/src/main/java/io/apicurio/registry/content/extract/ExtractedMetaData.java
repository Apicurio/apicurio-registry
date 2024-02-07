package io.apicurio.registry.content.extract;

import java.util.Map;

public class ExtractedMetaData {

    private String name;
    private String description;
    private Map<String, String> labels;
    
    /**
     * Constructor.
     */
    public ExtractedMetaData() {
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
    public Map<String, String> getLabels() {
        return labels;
    }

    /**
     * @param labels the labels to set
     */
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

}
