/*
 * Copyright 2020 Red Hat Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.sql;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;
import io.apicurio.common.apps.config.Info;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.metrics.health.liveness.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.health.readiness.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.storage.RegistryStorage;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


/**
 * An in-memory SQL implementation of the {@link RegistryStorage} interface.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@StorageMetricsApply
@Logged
public class InMemoryRegistryStorage extends AbstractSqlRegistryStorage {

    @Inject
    Logger logger;

    @Inject
    Logger log;

    @ConfigProperty(name = "registry.datasource.db-kind", defaultValue = "h2")
    @Info(category = "storage", description = "Datasource Db kind", availableSince = "2.0.0.Final")
    String databaseType;

    @ConfigProperty(name = "registry.datasource.jdbc.url", defaultValue = "jdbc:h2:mem:registry_db")
    @Info(category = "storage", description = "Datasource Db kind", availableSince = "2.0.0.Final")
    String jdbcUrl;

    @ConfigProperty(name = "registry.datasource.username", defaultValue = "sa")
    @Info(category = "storage", description = "Datasource Db kind", availableSince = "2.0.0.Final")
    String username;

    @ConfigProperty(name = "registry.datasource.password", defaultValue = "sa")
    @Info(category = "storage", description = "Datasource Db kind", availableSince = "2.0.0.Final")
    String password;

    @ConfigProperty(name = "registry.datasource.jdbc.initial-size", defaultValue = "20")
    @Info(category = "storage", description = "Datasource Db kind", availableSince = "2.0.0.Final")
    String initialSize;

    @ConfigProperty(name = "registry.datasource.jdbc.min-size", defaultValue = "20")
    @Info(category = "storage", description = "Datasource Db kind", availableSince = "2.0.0.Final")
    String minSize;

    @ConfigProperty(name = "registry.datasource.jdbc.max-size", defaultValue = "100")
    @Info(category = "storage", description = "Datasource Db kind", availableSince = "2.0.0.Final")
    String maxSize;

    @ConfigProperty(name = "registry.datasource.driver-classname", defaultValue = "org.h2.Driver")
    @Info(category = "storage", description = "Datasource Db kind", availableSince = "2.0.0.Final")
    String driverClassName;

    @PostConstruct
    void onConstruct() throws SQLException {

        log.debug("Creating an instance of ISqlStatements for DB: " + databaseType);

        Map<String, String> props = new HashMap<>();

        props.put(AgroalPropertiesReader.MAX_SIZE, maxSize);
        props.put(AgroalPropertiesReader.MIN_SIZE, minSize);
        props.put(AgroalPropertiesReader.INITIAL_SIZE, initialSize);
        props.put(AgroalPropertiesReader.JDBC_URL, jdbcUrl);
        props.put(AgroalPropertiesReader.PRINCIPAL, username);
        props.put(AgroalPropertiesReader.CREDENTIAL, password);
        props.put(AgroalPropertiesReader.PROVIDER_CLASS_NAME, driverClassName);

        AgroalDataSource datasource = AgroalDataSource.from(new AgroalPropertiesReader()
                .readProperties(props)
                .get());


        log.info("Using In Memory (H2) SQL storage.");
        initialize(new DefaultHandleFactory(datasource, logger), true);


    }

    /**
     * @see io.apicurio.registry.storage.RegistryStorage#storageName()
     */
    @Override
    public String storageName() {
        return "in-memory";
    }
}
