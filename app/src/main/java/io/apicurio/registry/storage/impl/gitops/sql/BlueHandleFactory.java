package io.apicurio.registry.storage.impl.gitops.sql;

import io.agroal.api.AgroalDataSource;
import io.apicurio.registry.storage.impl.sql.AbstractHandleFactory;
import org.slf4j.Logger;


public class BlueHandleFactory extends AbstractHandleFactory {

    public BlueHandleFactory(AgroalDataSource dataSource, Logger log) {
        initialize(dataSource, "blue", log);
    }
}
