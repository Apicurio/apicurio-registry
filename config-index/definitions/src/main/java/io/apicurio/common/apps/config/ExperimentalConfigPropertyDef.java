package io.apicurio.common.apps.config;

/**
 * Represents a configuration property marked as experimental, discovered at build time.
 */
public class ExperimentalConfigPropertyDef {

    private String name;
    private String description;

    public ExperimentalConfigPropertyDef() {
    }

    public ExperimentalConfigPropertyDef(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "ExperimentalConfigPropertyDef [name=" + name + "]";
    }
}
