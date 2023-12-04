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

package io.apicurio.registry.utils.tests;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class PostgreSqlEmbeddedTestResource implements QuarkusTestResourceLifecycleManager {

    private EmbeddedPostgres database;

    /**
     * @see QuarkusTestResourceLifecycleManager#start()
     */
    @Override
    public Map<String, String> start() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests")) && isPostgresqlStorage()) {

            String currentEnv = System.getenv("CURRENT_ENV");

            if (currentEnv != null && "mas".equals(currentEnv)) {
                Map<String, String> props = new HashMap<>();
                props.put("registry.storage.db-kind", "postgresql");
                props.put("registry.datasource.url", "jdbc:postgresql://localhost:5432/test");
                props.put("registry.datasource.username", "test");
                props.put("registry.datasource.password", "test");
                return props;
            } else {
                return startPostgresql();
            }
        }
        return Collections.emptyMap();
    }

    private static boolean isPostgresqlStorage() {
        return ConfigProvider.getConfig().getValue("registry.storage.db-kind", String.class).equals("postgresql");
    }

    private Map<String, String> startPostgresql() {
        try {
            database = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        String datasourceUrl = database.getJdbcUrl("postgres", "postgres");

        Map<String, String> props = new HashMap<>();
        props.put("registry.storage.db-kind", "postgresql");
        props.put("registry.datasource.url", datasourceUrl);
        props.put("registry.datasource.username", "postgres");
        props.put("registry.datasource.password", "postgres");

        return props;
    }

    /**
     * @see QuarkusTestResourceLifecycleManager#stop()
     */
    @Override
    public void stop() {
        try {
            if (database != null) {
                database.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
