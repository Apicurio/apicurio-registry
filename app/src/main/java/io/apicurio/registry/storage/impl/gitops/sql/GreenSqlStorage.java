package io.apicurio.registry.storage.impl.gitops.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.common.apps.config.Info;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@ApplicationScoped
@Logged
public class GreenSqlStorage extends AbstractSqlRegistryStorage {

    @Inject
    Logger logger;

    @Inject
    @Named("green")
    AgroalDataSource dataSource;

    @ConfigProperty(name = "registry.storage.kind")
    @Info
    String registryStorageType;

    @PostConstruct
    void postConstruct() {
        if (registryStorageType.equals("gitops")) {
            initialize(new GreenHandleFactory(dataSource, logger), false);
        }
    }
}
