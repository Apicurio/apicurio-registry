package io.apicurio.registry.storage.impl.jdbc;

import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.StorageException;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import java.util.Optional;
import java.util.Properties;

@ApplicationScoped
public class JdbcDatabaseManager {

    private static Logger log = LoggerFactory.getLogger(JdbcDatabaseManager.class);

    @ConfigProperty(name = "registry.storage.type")
    private Optional<String> storageType;

    private void onStart(@Observes StartupEvent event) {

        log.info("JDBC Database Manager is starting...");

        if (!storageType.isPresent()) {
            throw new RegistryStorageException("Could not initialize data storage. " +
                    "Configuration property 'registry.storage.type' not found.");
        }

        log.info("JDBC storage type: " + storageType.get());
    }

    private void onStop(@Observes ShutdownEvent event) {
        log.info("JDBC Database Manager is stopping...");
    }
}
