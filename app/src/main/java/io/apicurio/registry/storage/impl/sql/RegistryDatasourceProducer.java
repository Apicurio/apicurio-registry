package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

public class RegistryDatasourceProducer {

    @Inject
    Logger log;

    @Inject
    @Named("postgresql")
    AgroalDataSource postgreDatasource;

    @Inject
    @Named("h2")
    AgroalDataSource h2Datasource;

    @ConfigProperty(name = "registry.storage.db-kind", defaultValue = "postgresql")
    @Info(category = "storage", description = "Datasource Db kind", availableSince = "2.0.0.Final")
    String databaseType;

    @Produces @ApplicationScoped
    public RegistryDataSource produces() {
        log.debug("Creating an instance of ISqlStatements for DB: " + databaseType);
        if ("h2".equals(databaseType)) {
            return new RegistryDataSource(h2Datasource);
        }
        if ("mssql".equals(databaseType)) {
            return new RegistryDataSource(h2Datasource);
        }
        if ("postgresql".equals(databaseType)) {
            return new RegistryDataSource(postgreDatasource);
        }
        throw new RuntimeException("Unsupported database type: " + databaseType);
    }
}
