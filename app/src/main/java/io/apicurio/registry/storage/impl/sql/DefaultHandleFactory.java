package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import org.slf4j.Logger;

public class DefaultHandleFactory extends AbstractHandleFactory {
    public DefaultHandleFactory(AgroalDataSource dataSource, Logger logger) {
        initialize(dataSource, "default", logger);
    }
}
