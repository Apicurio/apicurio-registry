package io.apicurio.registry.storage.impl.gitops.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.ConnectionRetryConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;

@ApplicationScoped
@Logged
public class BlueSqlStorage extends AbstractSqlRegistryStorage {

    @Inject
    Logger logger;

    @Inject
    @Named("blue")
    AgroalDataSource dataSource;

    @Inject
    ConnectionRetryConfig retryConfig;

    @Override
    public void initialize() {
        initialize(new BlueHandleFactory(dataSource, logger, retryConfig), false);
    }
}
