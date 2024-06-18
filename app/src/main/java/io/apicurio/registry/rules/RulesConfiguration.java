package io.apicurio.registry.rules;

import io.apicurio.registry.utils.RegistryProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.Properties;

@ApplicationScoped
public class RulesConfiguration {

    @Produces
    @ApplicationScoped
    public RulesProperties rulesProperties(
            @RegistryProperties(value = { "apicurio.rules.global" }) Properties properties) {
        return new RulesPropertiesImpl(properties);
    }

}
