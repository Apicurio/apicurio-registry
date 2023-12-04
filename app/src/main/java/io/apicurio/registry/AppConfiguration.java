package io.apicurio.registry;

import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;

import io.apicurio.registry.utils.PropertiesUtil;

/**
 * Generic configuration.
 *
 */
@ApplicationScoped
public class AppConfiguration {

    @Produces
    public Properties properties(InjectionPoint ip) {
        return PropertiesUtil.properties(ip);
    }

}
