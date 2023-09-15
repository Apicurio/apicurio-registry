package io.apicurio.registry.storage.impl.gitops.sql;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@ApplicationScoped
@Logged
public class GreenSqlStorage extends AbstractSqlRegistryStorage {

    @Inject
    GreenHandleFactory handleFactory;


    @PostConstruct
    void postConstruct() {
        initialize(handleFactory, false);
    }
}
