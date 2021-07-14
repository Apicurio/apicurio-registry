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

package io.apicurio.registry.storage.impl.sql;

import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * @author eric.wittmann@gmail.com
 */
public class SqlStorageUpgradeTestProfile implements QuarkusTestProfile {

    private static final String JDBC_URL = "jdbc:h2:file:./target/test_dbs/" + UUID.randomUUID().toString();
    private static final String V1_DB_DDL = "https://raw.githubusercontent.com/Apicurio/apicurio-registry/2.0.x/app/src/main/resources/io/apicurio/registry/storage/impl/sql/h2.ddl";

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        // Note: we need to enable these properties so that we can access the role mapping REST API
        // If these are not set, then the role mapping REST API will fail with a 403
        props.put("registry.auth.role-based-authorization", "true");
        props.put("registry.auth.role-source", "application");
        return props;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Arrays.asList(new TestResourceEntry(H2Initializer.class));
    }

    public static class H2Initializer implements QuarkusTestResourceLifecycleManager {

        private Connection connection;

        /**
         * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#init(java.util.Map)
         */
        @Override
        public void init(Map<String, String> initArgs) {
            QuarkusTestResourceLifecycleManager.super.init(initArgs);
        }

        /**
         * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#start()
         */
        @Override
        public Map<String, String> start() {
            String jdbcUrl = JDBC_URL;
            try {
                connection = DriverManager.getConnection(jdbcUrl);
                connection.setAutoCommit(true);

                // Load and parse the V1 database DDL
                DdlParser parser = new DdlParser();
                List<String> dbStatements;
                try (InputStream input = new URL(V1_DB_DDL).openStream()) {
                    if (input == null) {
                        throw new SQLException("DDL not found!");
                    }
                    dbStatements = parser.parse(input);
                }

                // Execute all the V1 DDL statements
                for (String statement : dbStatements) {
                    PreparedStatement ps = connection.prepareStatement(statement);
                    ps.executeUpdate();
                    ps.close();
                }

                // Now load and parse some SQL to seed the DB with data
                try (InputStream input = getClass().getClassLoader().getResourceAsStream("SqlStorageUpgradeTest.dml")) {
                    if (input == null) {
                        throw new SQLException("DML not found!");
                    }
                    dbStatements = parser.parse(input);
                }

                // Execute all the V1 DML statements
                for (String statement : dbStatements) {
                    PreparedStatement ps = connection.prepareStatement(statement);
                    ps.executeUpdate();
                    ps.close();
                }

                connection.commit();
                connection.close();

                Map<String, String> props = new HashMap<>();
                props.put("quarkus.datasource.jdbc.url", jdbcUrl);
                return props;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#stop()
         */
        @Override
        public void stop() {
        }

    }

}
