package io.apicurio.registry.services;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import jakarta.annotation.Priority;

import java.util.Map;

@Priority(102)
public class HttpSslConfigInterceptor implements ConfigSourceInterceptor {

    private static final Map<String, String> QUARKUS_TO_APICURIO = Map.of(
        "quarkus.http.ssl.protocols", "apicurio.http.ssl.protocols",
        "quarkus.http.ssl.cipher-suites", "apicurio.http.ssl.cipher-suites"
    );

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        String apicurioName = QUARKUS_TO_APICURIO.get(name);
        if (apicurioName == null) {
            return context.proceed(name);
        }

        // If the quarkus property is already explicitly set, respect it
        ConfigValue existing = context.proceed(name);
        if (existing != null && existing.getValue() != null 
                && !existing.getValue().isBlank()
                && !"DefaultValuesConfigSource".equals(existing.getConfigSourceName())) {
            return existing;
        }

        // Check for the apicurio equivalent
        ConfigValue apicurioValue = context.proceed(apicurioName);
        if (apicurioValue != null && apicurioValue.getValue() != null 
                && !apicurioValue.getValue().isBlank()) {
            return ConfigValue.builder()
                    .withName(name)
                    .withValue(apicurioValue.getValue())
                    .withConfigSourceName(apicurioValue.getConfigSourceName())
                    .build();
        }

        // No override — return whatever the chain resolves (preserves JVM defaults)
        return existing;
    }
}
