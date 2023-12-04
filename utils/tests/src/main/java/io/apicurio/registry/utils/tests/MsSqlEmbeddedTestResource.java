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

package io.apicurio.registry.utils.tests;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class MsSqlEmbeddedTestResource implements QuarkusTestResourceLifecycleManager {
    
    private static final String DB_PASSWORD = "P4ssw0rd!#";

    private static final DockerImageName IMAGE = DockerImageName.parse("mcr.microsoft.com/mssql/server").withTag("2022-latest");
    private MSSQLServerContainer<?> database = new MSSQLServerContainer<>(IMAGE)
            .withPassword(DB_PASSWORD)
            .acceptLicense();
    
    /**
     * Constructor.
     */
    public MsSqlEmbeddedTestResource() {
        System.out.println("[MsSqlEmbeddedTestResource] MS SQLServer test resource constructed.");
    }

    /**
     * @see QuarkusTestResourceLifecycleManager#start()
     */
    @Override
    public Map<String, String> start() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests")) && isMssqlStorage()) {

            System.out.println("[MsSqlEmbeddedTestResource] MS SQLServer test resource starting.");

            String currentEnv = System.getenv("CURRENT_ENV");

            if ("mas".equals(currentEnv)) {
                Map<String, String> props = new HashMap<>();
                props.put("registry.storage.db-kind", "mssql");
                props.put("registry.datasource.url", "jdbc:sqlserver://mssql;");
                props.put("registry.datasource.username", "test");
                props.put("registry.datasource.password", "test");
                return props;
            } else {
                return startMsSql();
            }
        }
        return Collections.emptyMap();
    }

    private static boolean isMssqlStorage() {
        return ConfigProvider.getConfig().getValue("registry.storage.db-kind", String.class).equals("mssql");
    }

    private Map<String, String> startMsSql() {
        database.start();

        String datasourceUrl = database.getJdbcUrl();

        Map<String, String> props = new HashMap<>();
        props.put("registry.storage.db-kind", "mssql");
        props.put("registry.datasource.url", datasourceUrl);
        props.put("registry.datasource.username", "SA");
        props.put("registry.datasource.password", DB_PASSWORD);
        return props;
    }

    /**
     * @see QuarkusTestResourceLifecycleManager#stop()
     */
    @Override
    public void stop() {
        if (database != null) {
            database.close();
        }
        System.out.println("[MsSqlEmbeddedTestResource] MS SQLServer test resource stopped.");
    }

}
