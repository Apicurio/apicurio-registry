package io.apicurio.registry.storage.impl.ksql.sql;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;

@ApplicationScoped
@Named("SQLRegistryStorage")
public class SQLRegistryStorage extends AbstractSqlRegistryStorage{

    private static final Logger log = LoggerFactory.getLogger(SQLRegistryStorage.class);

    @PostConstruct
    void onConstruct() {
        log.info("Using internal SQL storage.");
    }

}
