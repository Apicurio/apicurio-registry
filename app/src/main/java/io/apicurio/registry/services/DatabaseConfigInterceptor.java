package io.apicurio.registry.services;

import io.apicurio.registry.storage.impl.sql.RegistryDatabaseKind;
import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import jakarta.annotation.Priority;

/**
 * Configuration interceptor that dynamically activates/deactivates datasources
 * based on the configured storage kind. This ensures only the appropriate datasource
 * is active at runtime.
 *
 * In Quarkus 3.30+, this also handles JDBC URL configuration to prevent validation
 * errors for inactive datasources by returning null for their URLs (which tells
 * Quarkus the datasource is not configured).
 */
@Priority(100)
public class DatabaseConfigInterceptor implements ConfigSourceInterceptor {

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        ConfigValue storageKindValue = context.proceed("apicurio.storage.sql.kind");
        if (storageKindValue == null || storageKindValue.getValue() == null) {
            return context.proceed(name);
        }
        RegistryDatabaseKind databaseKind = RegistryDatabaseKind.valueOf(storageKindValue.getValue());

        // Handle active flag for each datasource
        switch (name) {
            case "quarkus.datasource.postgresql.active" -> {
                return createBooleanConfigValue(name, databaseKind.equals(RegistryDatabaseKind.postgresql));
            }
            case "quarkus.datasource.mssql.active" -> {
                return createBooleanConfigValue(name, databaseKind.equals(RegistryDatabaseKind.mssql));
            }
            case "quarkus.datasource.mysql.active" -> {
                return createBooleanConfigValue(name, databaseKind.equals(RegistryDatabaseKind.mysql));
            }
            case "quarkus.datasource.h2.active" -> {
                return createBooleanConfigValue(name, databaseKind.equals(RegistryDatabaseKind.h2));
            }
            // Handle JDBC URL for inactive datasources to prevent driver validation errors
            case "quarkus.datasource.postgresql.jdbc.url" -> {
                if (!databaseKind.equals(RegistryDatabaseKind.postgresql)) {
                    return null; // Return null to indicate datasource is not configured
                }
            }
            case "quarkus.datasource.mssql.jdbc.url" -> {
                if (!databaseKind.equals(RegistryDatabaseKind.mssql)) {
                    return null;
                }
            }
            case "quarkus.datasource.mysql.jdbc.url" -> {
                if (!databaseKind.equals(RegistryDatabaseKind.mysql)) {
                    return null;
                }
            }
            case "quarkus.datasource.h2.jdbc.url" -> {
                if (!databaseKind.equals(RegistryDatabaseKind.h2)) {
                    return null;
                }
            }
        }

        return context.proceed(name);
    }

    private ConfigValue createBooleanConfigValue(String name, boolean value) {
        return ConfigValue.builder().withName(name).withValue(String.valueOf(value)).build();
    }
}
