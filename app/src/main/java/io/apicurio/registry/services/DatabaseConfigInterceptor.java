package io.apicurio.registry.services;

import io.apicurio.registry.storage.impl.sql.RegistryDatabaseKind;
import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import jakarta.annotation.Priority;

/**
 * Automatically activates the correct Quarkus named datasource based on
 * {@code apicurio.storage.sql.kind}. Only provides a default when the
 * property has not been explicitly set by a higher-priority source (e.g.
 * a test profile or environment variable).
 */
@Priority(100)
public class DatabaseConfigInterceptor implements ConfigSourceInterceptor {

    private static final String H2_ACTIVE = "quarkus.datasource.h2.active";
    private static final String PG_ACTIVE = "quarkus.datasource.postgresql.active";
    private static final String MYSQL_ACTIVE = "quarkus.datasource.mysql.active";
    private static final String MSSQL_ACTIVE = "quarkus.datasource.mssql.active";

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        switch (name) {
            case H2_ACTIVE, PG_ACTIVE, MYSQL_ACTIVE, MSSQL_ACTIVE -> {
                // If the property is already explicitly set, respect it.
                ConfigValue existing = context.proceed(name);
                if (existing != null && existing.getValue() != null) {
                    return existing;
                }
                // Otherwise derive the value from apicurio.storage.sql.kind.
                ConfigValue storageKind = context.proceed("apicurio.storage.sql.kind");
                if (storageKind == null || storageKind.getValue() == null) {
                    return context.proceed(name);
                }
                RegistryDatabaseKind databaseKind = RegistryDatabaseKind.valueOf(storageKind.getValue());
                boolean active = switch (name) {
                    case H2_ACTIVE -> databaseKind == RegistryDatabaseKind.h2;
                    case PG_ACTIVE -> databaseKind == RegistryDatabaseKind.postgresql;
                    case MYSQL_ACTIVE -> databaseKind == RegistryDatabaseKind.mysql;
                    case MSSQL_ACTIVE -> databaseKind == RegistryDatabaseKind.mssql;
                    default -> false;
                };
                return ConfigValue.builder().withName(name).withValue(String.valueOf(active)).build();
            }
            default -> {
                return context.proceed(name);
            }
        }
    }

}
