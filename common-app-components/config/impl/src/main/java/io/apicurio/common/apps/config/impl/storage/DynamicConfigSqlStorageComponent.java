/*
 * Copyright 2021 Red Hat
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

package io.apicurio.common.apps.config.impl.storage;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigSqlStorageStatements;
import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.common.apps.logging.LoggerProducer;
import io.apicurio.common.apps.storage.exceptions.NotFoundException;
import io.apicurio.common.apps.storage.sql.jdbi.HandleFactory;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.apicurio.common.apps.storage.sql.jdbi.query.Sql.RESOURCE_CONTEXT_KEY;
import static io.apicurio.common.apps.storage.sql.jdbi.query.Sql.RESOURCE_IDENTIFIER_CONTEXT_KEY;
import static java.util.Objects.requireNonNull;

/**
 * @author eric.wittmann@gmail.com
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@ApplicationScoped
public class DynamicConfigSqlStorageComponent implements DynamicConfigStorage {

    public static String RESOURCE_CONTEXT_KEY_DCP = "dynamic configuration property";

    private Logger log;

    private HandleFactory handles;

    private DynamicConfigSqlStorageStatements sqlStatements;

    private volatile boolean isStarting;
    private volatile boolean ready;

    public synchronized void start(LoggerProducer loggerProducer, HandleFactory handles,
            DynamicConfigSqlStorageStatements sqlStatements) {
        if (isStarting) {
            throw new RuntimeException("The DynamicConfigSqlStorageComponent can be started only once");
        }
        isStarting = true;
        requireNonNull(loggerProducer);
        this.log = loggerProducer.getLogger(getClass());
        requireNonNull(handles);
        this.handles = handles;
        requireNonNull(sqlStatements);
        this.sqlStatements = sqlStatements;
        this.ready = true;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public List<DynamicConfigPropertyDto> getConfigProperties() {
        log.debug("Getting all config properties.");
        return handles.withHandleNoExceptionMapped(handle -> {
            String sql = sqlStatements.selectConfigProperties();
            return handle.createQuery(sql).setContext(RESOURCE_CONTEXT_KEY, RESOURCE_CONTEXT_KEY_DCP)
                    .map(new DynamicConfigPropertyDtoMapper()).list().stream()
                    // Filter out possible null values.
                    .filter(Objects::nonNull).collect(Collectors.toList());
        });
    }

    @Override
    public DynamicConfigPropertyDto getConfigProperty(String propertyName) {
        log.debug("Selecting a single config property: {}", propertyName);
        return handles.withHandleNoExceptionMapped(handle -> {

            String sql = sqlStatements.selectConfigPropertyByName();
            Optional<DynamicConfigPropertyDto> res = handle.createQuery(sql)
                    .setContext(RESOURCE_CONTEXT_KEY, RESOURCE_CONTEXT_KEY_DCP)
                    .setContext(RESOURCE_IDENTIFIER_CONTEXT_KEY, propertyName).bind(0, propertyName)
                    .map(new DynamicConfigPropertyDtoMapper()).findOne();

            return res.orElse(null);
        });
    }

    @Override
    public void setConfigProperty(DynamicConfigPropertyDto property) {
        log.debug("Setting a config property with name: {}  and value: {}", property.getName(),
                property.getValue());
        handles.withHandleNoExceptionMapped(handle -> {
            String propertyName = property.getName();
            String propertyValue = property.getValue();
            // TODO: Why delete and recreate? Can be replaced by upsert?

            // First delete the property row from the table
            String sql = sqlStatements.deleteConfigProperty();
            handle.createUpdate(sql).setContext(RESOURCE_CONTEXT_KEY, RESOURCE_CONTEXT_KEY_DCP)
                    .setContext(RESOURCE_IDENTIFIER_CONTEXT_KEY, propertyName).bind(0, propertyName)
                    .execute();

            // Then create the row again with the new value
            sql = sqlStatements.insertConfigProperty();
            handle.createUpdate(sql).setContext(RESOURCE_CONTEXT_KEY, RESOURCE_CONTEXT_KEY_DCP)
                    .setContext(RESOURCE_IDENTIFIER_CONTEXT_KEY, propertyName).bind(0, propertyName)
                    .bind(1, propertyValue).bind(2, System.currentTimeMillis()).execute();

            return null;
        });
    }

    @Override
    public void deleteConfigProperty(String propertyName) {
        log.debug("Deleting a config property from storage: {}", propertyName);
        handles.withHandleNoExceptionMapped(handle -> {

            String sql = sqlStatements.deleteConfigProperty();
            int rows = handle.createUpdate(sql).setContext(RESOURCE_CONTEXT_KEY, RESOURCE_CONTEXT_KEY_DCP)
                    .setContext(RESOURCE_IDENTIFIER_CONTEXT_KEY, propertyName).bind(0, propertyName)
                    .execute();

            if (rows == 0) {
                throw new NotFoundException("Property value not currently set: " + propertyName,
                        Map.of(RESOURCE_CONTEXT_KEY, RESOURCE_CONTEXT_KEY_DCP,
                                RESOURCE_IDENTIFIER_CONTEXT_KEY, propertyName));
            }
            return null;
        });
    }

    protected List<String> getTenantsWithStaleConfigProperties(Instant since) {
        log.debug("Getting all tenant IDs with stale config properties.");
        return handles.withHandleNoExceptionMapped(handle -> {
            String sql = sqlStatements.selectTenantIdsByConfigModifiedOn();
            return handle.createQuery(sql).bind(0, since.toEpochMilli()).mapTo(String.class).list();
        });
    }
}
