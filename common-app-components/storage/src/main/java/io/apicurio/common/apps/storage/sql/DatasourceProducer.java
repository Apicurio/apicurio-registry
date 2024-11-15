package io.apicurio.common.apps.storage.sql;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;
import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class DatasourceProducer {

    @Inject
    Logger log;

    @ConfigProperty(name = "apicurio.storage.db-kind", defaultValue = "h2")
    @Info(category = "storage", description = "Application datasource database type", availableSince = "0.2.5.Final", registryAvailableSince = "3.0.0", studioAvailableSince = "1.0.0")
    String databaseType;

    @ConfigProperty(name = "apicurio.datasource.url", defaultValue = "jdbc:h2:mem:registry_db")
    @Info(category = "storage", description = "Application datasource jdbc url", availableSince = "0.2.5.Final", registryAvailableSince = "3.0.0", studioAvailableSince = "1.0.0")
    String jdbcUrl;

    @ConfigProperty(name = "apicurio.datasource.username", defaultValue = "sa")
    @Info(category = "storage", description = "Application datasource username", availableSince = "0.2.5.Final", registryAvailableSince = "3.0.0", studioAvailableSince = "1.0.0")
    String username;

    @ConfigProperty(name = "apicurio.datasource.password", defaultValue = "sa")
    @Info(category = "storage", description = "Application datasource password", availableSince = "0.2.5.Final", registryAvailableSince = "3.0.0", studioAvailableSince = "1.0.0")
    String password;

    @ConfigProperty(name = "apicurio.datasource.jdbc.initial-size", defaultValue = "20")
    @Info(category = "storage", description = "Application datasource pool initial size", availableSince = "0.2.5.Final", registryAvailableSince = "3.0.0", studioAvailableSince = "1.0.0")
    String initialSize;

    @ConfigProperty(name = "apicurio.datasource.jdbc.min-size", defaultValue = "20")
    @Info(category = "storage", description = "Application datasource pool minimum size", availableSince = "0.2.5.Final", registryAvailableSince = "3.0.0", studioAvailableSince = "1.0.0")
    String minSize;

    @ConfigProperty(name = "apicurio.datasource.jdbc.max-size", defaultValue = "100")
    @Info(category = "storage", description = "Application datasource pool maximum size", availableSince = "0.2.5.Final", registryAvailableSince = "3.0.0", studioAvailableSince = "1.0.0")
    String maxSize;

    @ApplicationScoped
    @Named("apicurioDatasource")
    public AgroalDataSource produceDatasource() throws SQLException {
        log.debug("Creating an instance of ISqlStatements for DB: " + databaseType);

        final DatabaseKind databaseKind = DatabaseKind.valueOf(databaseType);

        Map<String, String> props = new HashMap<>();

        props.put(AgroalPropertiesReader.MAX_SIZE, maxSize);
        props.put(AgroalPropertiesReader.MIN_SIZE, minSize);
        props.put(AgroalPropertiesReader.INITIAL_SIZE, initialSize);
        props.put(AgroalPropertiesReader.JDBC_URL, jdbcUrl);
        props.put(AgroalPropertiesReader.PRINCIPAL, username);
        props.put(AgroalPropertiesReader.CREDENTIAL, password);
        props.put(AgroalPropertiesReader.PROVIDER_CLASS_NAME, databaseKind.getDriverClassName());

        AgroalDataSource datasource = AgroalDataSource
                .from(new AgroalPropertiesReader().readProperties(props).get());

        log.info("Using {} SQL storage.", databaseType);

        return datasource;
    }
}
