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

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;

public class GreenDatasourceProducer {

    @ConfigProperty(name = "apicurio.datasource.green.db-kind", defaultValue = "h2")
    @Info(category = CATEGORY_STORAGE, description = "Gitops green datasource db kind", availableSince = "3.0.0")
    String databaseType;

    @ConfigProperty(name = "apicurio.datasource.green.jdbc.url", defaultValue = "jdbc:h2:mem:registry_db")
    @Info(category = CATEGORY_STORAGE, description = "Gitops green datasource jdbc url", availableSince = "3.0.0")
    String jdbcUrl;

    @ConfigProperty(name = "apicurio.datasource.green.username", defaultValue = "sa")
    @Info(category = CATEGORY_STORAGE, description = "Gitops green datasource username", availableSince = "3.0.0")
    String username;

    @ConfigProperty(name = "apicurio.datasource.green.password", defaultValue = "sa")
    @Info(category = CATEGORY_STORAGE, description = "Gitops green datasource password", availableSince = "3.0.0")
    String password;

    @ConfigProperty(name = "apicurio.datasource.green.jdbc.initial-size", defaultValue = "20")
    @Info(category = CATEGORY_STORAGE, description = "Gitops green datasource pool initial size", availableSince = "3.0.0")
    String initialSize;

    @ConfigProperty(name = "apicurio.datasource.green.jdbc.min-size", defaultValue = "20")
    @Info(category = CATEGORY_STORAGE, description = "Gitops green datasource pool minimum size", availableSince = "3.0.0")
    String minSize;

    @ConfigProperty(name = "apicurio.datasource.green.jdbc.max-size", defaultValue = "100")
    @Info(category = CATEGORY_STORAGE, description = "Gitops green datasource pool max size", availableSince = "3.0.0")
    String maxSize;

    @Produces
    @ApplicationScoped
    @Named("green")
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
