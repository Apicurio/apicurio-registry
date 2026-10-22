package io.apicurio.registry;

import io.apicurio.registry.utils.PropertiesUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.util.Properties;

/**
 * Generic configuration.
 */
@ApplicationScoped
public class AppConfiguration {

    @Produces
    public Properties properties(InjectionPoint ip) {
        return PropertiesUtil.properties(ip);
    }

}
