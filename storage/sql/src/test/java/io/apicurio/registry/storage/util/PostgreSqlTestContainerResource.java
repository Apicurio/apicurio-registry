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

package io.apicurio.registry.storage.util;

import java.util.HashMap;
import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * @author Fabian Martinez
 */
public class PostgreSqlTestContainerResource implements QuarkusTestResourceLifecycleManager {

    @SuppressWarnings("rawtypes")
    private PostgreSQLContainer database;

    /**
     * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#start()
     */
    @Override
    public Map<String, String> start() {
        database = new PostgreSQLContainer<>("postgres:12-alpine");
        database.start();

        String datasourceUrl = "jdbc:postgresql://" + database.getContainerIpAddress() + ":" +
                database.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database.getDatabaseName();

        Map<String, String> props = new HashMap<>();
        props.put("quarkus.datasource.jdbc.url", datasourceUrl);
        props.put("quarkus.datasource.username", database.getUsername());
        props.put("quarkus.datasource.password", database.getPassword());
        return props;
    }

    /**
     * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#stop()
     */
    @Override
    public void stop() {
        database.stop();
    }

}
