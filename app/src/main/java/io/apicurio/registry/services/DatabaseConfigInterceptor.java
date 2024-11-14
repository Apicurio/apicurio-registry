package io.apicurio.registry.services;

import io.apicurio.registry.storage.impl.sql.RegistryDatabaseKind;
import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import jakarta.annotation.Priority;

@Priority(100)
public class DatabaseConfigInterceptor implements ConfigSourceInterceptor {

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        ConfigValue storageKind = context.proceed("apicurio.storage.sql.kind");
        RegistryDatabaseKind databaseKind = RegistryDatabaseKind.valueOf(storageKind.getValue());

        switch (name) {
            case "quarkus.datasource.postgresql.active" -> {
                if (databaseKind.equals(RegistryDatabaseKind.postgresql)) {
                    return ConfigValue.builder().withName(name).withValue("true").build();
                } else {
                    return ConfigValue.builder().withName(name).withValue("false").build();
                }
            }
            case "quarkus.datasource.mssql.active" -> {
                if (databaseKind.equals(RegistryDatabaseKind.mssql)) {
                    return ConfigValue.builder().withName(name).withValue("true").build();
                } else {
                    return ConfigValue.builder().withName(name).withValue("false").build();
                }
            }
            case "quarkus.datasource.mysql.active" -> {
                if (databaseKind.equals(RegistryDatabaseKind.mysql)) {
                    return ConfigValue.builder().withName(name).withValue("true").build();
                } else {
                    return ConfigValue.builder().withName(name).withValue("false").build();
                }
            }
            case "quarkus.datasource.h2.active" -> {
                if (databaseKind.equals(RegistryDatabaseKind.h2)) {
                    return ConfigValue.builder().withName(name).withValue("true").build();
                } else {
                    return ConfigValue.builder().withName(name).withValue("false").build();
                }
            }
        }

        return context.proceed(name);
    }

}
