package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class RuleConfigurationDto {

    private String configuration; // TODO why not a map?

    /**
     * Constructor.
     */
    public RuleConfigurationDto() {
    }

    /**
     * @return the configuration
     */
    public String getConfiguration() {
        return configuration;
    }

    /**
     * @param configuration the configuration to set
     */
    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }
    
}
