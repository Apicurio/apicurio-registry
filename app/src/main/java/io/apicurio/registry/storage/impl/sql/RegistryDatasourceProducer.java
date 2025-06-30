package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.sql.SQLException;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

public class RegistryDatasourceProducer {

    @Inject
    Logger log;

    @ConfigProperty(name = "apicurio.storage.sql.kind", defaultValue = "h2")
    @Info(category = CATEGORY_STORAGE, description = "Application datasource database type", availableSince = "3.0.0")
    String databaseType;

    @Inject
    @Named("h2")
    AgroalDataSource h2Datasource;

    @Inject
    @Named("postgresql")
    AgroalDataSource postgresqlDatasource;

    @Inject
    @Named("mysql")
    AgroalDataSource mysqlDatasource;

    @Inject
    @Named("mssql")
    AgroalDataSource mssqlDatasource;

    @Produces
    @ApplicationScoped
    @Named("application")
    public AgroalDataSource produceDatasource() throws SQLException {
        log.debug("Creating an instance of ISqlStatements for DB: " + databaseType);

        final RegistryDatabaseKind databaseKind = RegistryDatabaseKind.valueOf(databaseType);

        log.info("Using {} SQL storage.", databaseType);

        switch (databaseKind) {
            case h2 -> {
                return h2Datasource;
            }
            case postgresql -> {
                return postgresqlDatasource;
            }
            case mysql -> {
                return mysqlDatasource;
            }
            case mssql -> {
                return mssqlDatasource;
            }
            default -> throw new IllegalStateException(
                    String.format("unrecognized database type: %s", databaseKind.name()));
        }

    }
}
