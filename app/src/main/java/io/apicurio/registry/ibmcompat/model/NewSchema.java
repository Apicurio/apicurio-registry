package io.apicurio.registry.ibmcompat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import javax.validation.constraints.NotNull;

@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class NewSchema extends NewSchemaVersion {

    private String name;
    private SchemaState state;
    private boolean enabled;

    @JsonProperty("name")
    @NotNull
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("state")
    public SchemaState getState() {
        return state;
    }

    public void setState(SchemaState state) {
        this.state = state;
    }

    @JsonProperty("enabled")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        NewSchema newSchema = (NewSchema) o;
        return enabled == newSchema.enabled &&
               Objects.equals(name, newSchema.name) &&
               Objects.equals(state, newSchema.state);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, state, enabled);
    }

    @Override
    public String toString() {
        return "NewSchema{" +
               "name='" + name + '\'' +
               ", state=" + state +
               ", enabled=" + enabled +
               '}';
    }
}

