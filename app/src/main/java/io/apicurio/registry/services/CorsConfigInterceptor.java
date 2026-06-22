package io.apicurio.registry.services;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import jakarta.annotation.Priority;

@Priority(101)
public class CorsConfigInterceptor implements ConfigSourceInterceptor {

    private static final String CORS_ORIGINS_PROPERTY = "quarkus.http.cors.origins";
    private static final String URL_OVERRIDE_HOST_PROPERTY = "apicurio.url.override.host";
    private static final String URL_OVERRIDE_PORT_PROPERTY = "apicurio.url.override.port";

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        if (!CORS_ORIGINS_PROPERTY.equals(name)) {
            return context.proceed(name);
        }

        ConfigValue baseValue = context.proceed(name);
        ConfigValue hostValue = context.proceed(URL_OVERRIDE_HOST_PROPERTY);

        if (hostValue == null || hostValue.getValue() == null || hostValue.getValue().isBlank()) {
            return baseValue;
        }

        String host = hostValue.getValue().trim();
        String portSuffix = buildPortSuffix(context);
        String autoOrigins = "http://" + host + portSuffix + ",https://" + host + portSuffix;

        String combined;
        if (baseValue != null && baseValue.getValue() != null && !baseValue.getValue().isBlank()) {
            combined = baseValue.getValue() + "," + autoOrigins;
        } else {
            combined = autoOrigins;
        }

        return ConfigValue.builder().withName(name).withValue(combined).build();
    }

    private String buildPortSuffix(ConfigSourceInterceptorContext context) {
        ConfigValue portValue = context.proceed(URL_OVERRIDE_PORT_PROPERTY);
        if (portValue == null || portValue.getValue() == null || portValue.getValue().isBlank()) {
            return "";
        }

        try {
            int port = Integer.parseInt(portValue.getValue().trim());
            if (port == 80 || port == 443 || port <= 0) {
                return "";
            }
            return ":" + port;
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
