package io.apicurio.registry.storage.impl.gitops.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.storage.impl.sql.AbstractHandleFactory;
import io.apicurio.registry.storage.impl.sql.ConnectionRetryConfig;
import org.slf4j.Logger;

public class BlueHandleFactory extends AbstractHandleFactory {

    public BlueHandleFactory(AgroalDataSource dataSource, Logger log,
            ConnectionRetryConfig retryConfig) {
        initialize(dataSource, "blue", log, retryConfig);
    }
}
