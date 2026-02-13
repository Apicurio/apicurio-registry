package io.apicurio.registry.storage.impl.gitops.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.storage.impl.sql.AbstractHandleFactory;
import io.apicurio.registry.storage.impl.sql.ConnectionRetryConfig;
import org.slf4j.Logger;

public class GreenHandleFactory extends AbstractHandleFactory {

    public GreenHandleFactory(AgroalDataSource dataSource, Logger log,
            ConnectionRetryConfig retryConfig) {
        initialize(dataSource, "green", log, retryConfig);
    }
}
