package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;

public class HandleFactoryProducer {

    @Inject
    @Named("application")
    AgroalDataSource dataSource;

    @Inject
    Logger logger;

    @Inject
    ConnectionRetryConfig retryConfig;

    @Produces
    @ApplicationScoped
    public HandleFactory produceHandleFactory() {
        return new DefaultHandleFactory(dataSource, logger, retryConfig);
    }
}
