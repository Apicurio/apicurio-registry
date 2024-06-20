package io.apicurio.registry.storage.impl.gitops;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.impl.sql.RegistryDatabaseKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class BlueDatasourceProducer {

    @ConfigProperty(name = "apicurio.datasource.blue.db-kind", defaultValue = "h2")
    @Info(category = "storage", description = "Gitops blue datasource db kind", availableSince = "3.0.0.Final")
    String databaseType;

    @ConfigProperty(name = "apicurio.datasource.blue.jdbc.url", defaultValue = "jdbc:h2:mem:registry_db")
    @Info(category = "storage", description = "Gitops blue datasource jdbc url", availableSince = "3.0.0.Final")
    String jdbcUrl;

    @ConfigProperty(name = "apicurio.datasource.blue.username", defaultValue = "sa")
    @Info(category = "storage", description = "Gitops blue datasource username", availableSince = "3.0.0.Final")
    String username;

    @ConfigProperty(name = "apicurio.datasource.blue.password", defaultValue = "sa")
    @Info(category = "storage", description = "Gitops blue datasource password", availableSince = "3.0.0.Final")
    String password;

    @ConfigProperty(name = "apicurio.datasource.blue.jdbc.initial-size", defaultValue = "20")
    @Info(category = "storage", description = "Gitops blue datasource pool initial size", availableSince = "3.0.0.Final")
    String initialSize;

    @ConfigProperty(name = "apicurio.datasource.blue.jdbc.min-size", defaultValue = "20")
    @Info(category = "storage", description = "Gitops blue datasource pool minimum size", availableSince = "3.0.0.Final")
    String minSize;

    @ConfigProperty(name = "apicurio.datasource.blue.jdbc.max-size", defaultValue = "100")
    @Info(category = "storage", description = "Gitops blue datasource pool max size", availableSince = "3.0.0.Final")
    String maxSize;

    @Produces
    @ApplicationScoped
    @Named("blue")
    public AgroalDataSource produceDatasource() throws SQLException {
        final RegistryDatabaseKind databaseKind = RegistryDatabaseKind.valueOf(databaseType);

        Map<String, String> props = new HashMap<>();

        props.put(AgroalPropertiesReader.MAX_SIZE, maxSize);
        props.put(AgroalPropertiesReader.MIN_SIZE, minSize);
        props.put(AgroalPropertiesReader.INITIAL_SIZE, initialSize);
        props.put(AgroalPropertiesReader.JDBC_URL, jdbcUrl);
        props.put(AgroalPropertiesReader.PRINCIPAL, username);
        props.put(AgroalPropertiesReader.CREDENTIAL, password);
        props.put(AgroalPropertiesReader.PROVIDER_CLASS_NAME, databaseKind.getDriverClassName());

        return AgroalDataSource.from(new AgroalPropertiesReader().readProperties(props).get());
    }
}
