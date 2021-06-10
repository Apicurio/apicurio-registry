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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * @author Fabian Martinez
 */
public class PostgreSqlEmbeddedTestResource implements QuarkusTestResourceLifecycleManager {

    private EmbeddedPostgres database;

    /**
     * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#start()
     */
    @Override
    public Map<String, String> start() {

        try {
            database = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }


        String datasourceUrl = database.getJdbcUrl("postgres", "postgres");

        Map<String, String> props = new HashMap<>();
        props.put("quarkus.datasource.jdbc.url", datasourceUrl);
        props.put("quarkus.datasource.username", "postgres");
        props.put("quarkus.datasource.password", "postgres");
        return props;
    }

    /**
     * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#stop()
     */
    @Override
    public void stop() {
        try {
            database.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
