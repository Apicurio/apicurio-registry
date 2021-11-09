/*
 * Copyright 2018 Red Hat Inc
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.slf4j.Logger;

import io.apicurio.registry.config.RegistryConfigProperty;
import io.apicurio.registry.config.RegistryConfigService;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class SqlStatementsProducer {

    @Inject
    Logger log;

    @Inject
    RegistryConfigService configService;

    /**
     * Produces an {@link SqlStatements} instance for injection.
     */
    @Produces @ApplicationScoped
    public SqlStatements createSqlStatements() {
        String databaseType = configService.get(RegistryConfigProperty.REGISTRY_QUARKUS_DB_KIND);
        log.debug("Creating an instance of ISqlStatements for DB: " + databaseType);
        if ("h2".equals(databaseType)) {
            return new H2SqlStatements();
        }
        if ("postgresql".equals(databaseType)) {
            return new PostgreSQLSqlStatements();
        }
        throw new RuntimeException("Unsupported database type: " + databaseType);
    }

}
