package io.apicurio.registry.storage.impl.sql;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

@ApplicationScoped
public class SqlStatementsProducer {

    @Inject
    Logger log;

    @ConfigProperty(name = "apicurio.storage.sql.kind", defaultValue = "h2")
    @Info(category = CATEGORY_STORAGE, description = "Datasource Db kind", availableSince = "2.0.0.Final")
    String databaseType;

    /**
     * Produces an {@link SqlStatements} instance for injection.
     */
    @Produces
    @ApplicationScoped
    public SqlStatements createSqlStatements() {
        log.debug("Creating an instance of ISqlStatements for DB: " + databaseType);
        if ("h2".equals(databaseType)) {
            return new H2SqlStatements();
        }
        if ("mssql".equals(databaseType)) {
            return new SQLServerSqlStatements();
        }
        if ("postgresql".equals(databaseType)) {
            return new PostgreSQLSqlStatements();
        }
        if ("mysql".equals(databaseType)) {
            return new MySQLSqlStatements();
        }
        throw new RuntimeException("Unsupported database type: " + databaseType);
    }

}
