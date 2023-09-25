/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.storage.util;

import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * @author bruno.ariev@gmail.com
 */
public class MySqlEmbeddedTestResource implements QuarkusTestResourceLifecycleManager {
    
    private static final String DB_PASSWORD = "mysql";

    private static final DockerImageName IMAGE = DockerImageName
            .parse("mysql")
            .withTag("8.0-debian");

    private MySQLContainer database = new MySQLContainer<>(IMAGE)
            .withPassword("mysql");

    /**
     * Constructor.
     */
    public MySqlEmbeddedTestResource() {
        System.out.println("[MySqlEmbeddedTestResource] MySQL test resource constructed.");
    }

    /**
     * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#start()
     */
    @Override
    public Map<String, String> start() {
        System.out.println("[MySqlEmbeddedTestResource] MySQL test resource starting.");

        String currentEnv = System.getenv("CURRENT_ENV");

        if ("mas".equals(currentEnv)) {
            Map<String, String> props = new HashMap<>();
            props.put("quarkus.datasource.jdbc.url", "jdbc:sqlserver://mysql;");
            props.put("quarkus.datasource.username", "mysql");
            props.put("quarkus.datasource.password", "mysql");
            return props;
        } else {
            return startMySql();
        }
    }

    private Map<String, String> startMySql() {
        database.start();

        String datasourceUrl = database.getJdbcUrl();

        Map<String, String> props = new HashMap<>();
        props.put("quarkus.datasource.jdbc.url", datasourceUrl);
        props.put("quarkus.datasource.username", "SA");
        props.put("quarkus.datasource.password", DB_PASSWORD);
        return props;
    }

    /**
     * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#stop()
     */
    @Override
    public void stop() {
        if (database != null) {
            database.close();
        }
        System.out.println("[MsSqlEmbeddedTestResource] MS SQLServer test resource stopped.");
    }

}
