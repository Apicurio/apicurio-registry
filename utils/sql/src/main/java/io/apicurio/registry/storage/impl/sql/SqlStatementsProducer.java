/*
 * Copyright 2018 JBoss Inc
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

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class SqlStatementsProducer {
    private static Logger logger = LoggerFactory.getLogger(SqlStatementsProducer.class);

    @ConfigProperty(name = "quarkus.datasource.db-kind", defaultValue = "postgresql")
    String databaseType;
    
    /**
     * Produces an {@link SqlStatements} instance for injection.
     */
    @Produces @ApplicationScoped
    public SqlStatements createSqlStatements() {
        logger.debug("Creating an instance of ISqlStatements for DB: " + databaseType);
        if ("h2".equals(databaseType)) {
            return new H2SqlStatements();
        } else if ("postgresql".equals(databaseType)) {
            logger.info("Using postgres");
            return new PostgreSQLSqlStatements();
        } else {
            logger.info("Using CloudSpanner");
            return new CloudSpannerSqlStatements();
        }
//        throw new RuntimeException("Unsupported database type: " + databaseType);
    }

}
