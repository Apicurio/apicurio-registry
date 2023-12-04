package io.apicurio.registry.storage.impl.gitops.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;


@ApplicationScoped
@Logged
public class GreenSqlStorage extends AbstractSqlRegistryStorage {

    @Inject
    Logger logger;

    @Inject
    @Named("green")
    AgroalDataSource dataSource;

    @Override
    public void initialize() {
        initialize(new GreenHandleFactory(dataSource, logger), false);
    }
}
