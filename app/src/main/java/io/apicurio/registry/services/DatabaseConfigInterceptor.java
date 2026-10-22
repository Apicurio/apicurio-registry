package io.apicurio.registry.services;

import io.apicurio.registry.storage.impl.sql.RegistryDatabaseKind;
import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import jakarta.annotation.Priority;

/**
 * Automatically activates the correct Quarkus named datasource and JDBC
 * telemetry based on {@code apicurio.storage.sql.kind}. Only provides a
 * default when the property has not been explicitly set by a higher-priority
 * source (e.g. a test profile or environment variable).
 */
@Priority(100)
public class DatabaseConfigInterceptor implements ConfigSourceInterceptor {

    private static final String H2_ACTIVE = "quarkus.datasource.h2.active";
    private static final String PG_ACTIVE = "quarkus.datasource.postgresql.active";
    private static final String MYSQL_ACTIVE = "quarkus.datasource.mysql.active";
    private static final String MSSQL_ACTIVE = "quarkus.datasource.mssql.active";

    private static final String H2_TELEMETRY = "quarkus.datasource.h2.jdbc.telemetry";
    private static final String PG_TELEMETRY = "quarkus.datasource.postgresql.jdbc.telemetry";
    private static final String MYSQL_TELEMETRY = "quarkus.datasource.mysql.jdbc.telemetry";
    private static final String MSSQL_TELEMETRY = "quarkus.datasource.mssql.jdbc.telemetry";

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        switch (name) {
            case H2_ACTIVE, PG_ACTIVE, MYSQL_ACTIVE, MSSQL_ACTIVE,
                 H2_TELEMETRY, PG_TELEMETRY, MYSQL_TELEMETRY, MSSQL_TELEMETRY -> {
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
                    case H2_ACTIVE, H2_TELEMETRY -> databaseKind == RegistryDatabaseKind.h2;
                    case PG_ACTIVE, PG_TELEMETRY -> databaseKind == RegistryDatabaseKind.postgresql;
                    case MYSQL_ACTIVE, MYSQL_TELEMETRY -> databaseKind == RegistryDatabaseKind.mysql;
                    case MSSQL_ACTIVE, MSSQL_TELEMETRY -> databaseKind == RegistryDatabaseKind.mssql;
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
